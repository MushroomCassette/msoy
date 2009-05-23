//
// $Id$

package com.threerings.msoy.item.server;

import static com.threerings.msoy.Log.log;

import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import com.samskivert.util.ArrayIntSet;
import com.samskivert.util.CollectionUtil;
import com.samskivert.util.RandomUtil;

import com.threerings.msoy.admin.server.RuntimeConfig;
import com.threerings.msoy.data.StatType;
import com.threerings.msoy.data.UserAction;
import com.threerings.msoy.data.all.MediaDesc;

import com.threerings.msoy.server.MsoyEventLogger;
import com.threerings.msoy.server.ServerConfig;
import com.threerings.msoy.server.StatLogic;
import com.threerings.msoy.server.persist.CharityRecord;
import com.threerings.msoy.server.persist.MemberRecord;
import com.threerings.msoy.server.persist.TagPopularityRecord;
import com.threerings.msoy.server.persist.UserActionRepository;

import com.threerings.msoy.web.gwt.ServiceCodes;
import com.threerings.msoy.web.gwt.ServiceException;
import com.threerings.msoy.web.server.MsoyServiceServlet;

import com.threerings.msoy.game.server.GameLogic;
import com.threerings.msoy.game.server.persist.GameInfoRecord;
import com.threerings.msoy.game.server.persist.MsoyGameRepository;
import com.threerings.msoy.person.gwt.FeedMessageType;
import com.threerings.msoy.person.server.persist.FeedRepository;

import com.threerings.msoy.money.data.all.Currency;
import com.threerings.msoy.money.data.all.MoneyTransaction;
import com.threerings.msoy.money.data.all.PriceQuote;
import com.threerings.msoy.money.data.all.PurchaseResult;
import com.threerings.msoy.money.server.BuyResult;
import com.threerings.msoy.money.server.MoneyLogic;

import com.threerings.msoy.room.server.persist.MemoriesRecord;
import com.threerings.msoy.room.server.persist.MemoryRepository;

import com.threerings.msoy.item.data.ItemCodes;
import com.threerings.msoy.item.data.all.CatalogIdent;
import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.ItemIdent;
import com.threerings.msoy.item.data.all.IdentGameItem;
import com.threerings.msoy.item.gwt.CatalogListing;
import com.threerings.msoy.item.gwt.CatalogQuery;
import com.threerings.msoy.item.gwt.CatalogService;
import com.threerings.msoy.item.gwt.ItemPrices;
import com.threerings.msoy.item.gwt.ListingCard;
import com.threerings.msoy.item.gwt.ShopData;
import com.threerings.msoy.item.server.persist.CatalogRecord;
import com.threerings.msoy.item.server.persist.FavoriteItemRecord;
import com.threerings.msoy.item.server.persist.FavoritesRepository;
import com.threerings.msoy.item.server.persist.ItemRecord;
import com.threerings.msoy.item.server.persist.ItemRepository;

/**
 * Provides the server implementation of {@link CatalogService}.
 */
public class CatalogServlet extends MsoyServiceServlet
    implements CatalogService
{
    // from interface CatalogService
    public ShopData loadShopData ()
        throws ServiceException
    {
        ShopData data = new ShopData();

        // these members are our official 'favoriters' (no favoriting mature items!)
        ArrayIntSet memberIds = new ArrayIntSet(ServerConfig.getShopFavoriteMemberIds());

        // choose random TOP_ITEM_COUNT of TOP_ITEM_COUNT*2 recent favorite avatars & furni
        List<ListingCard> avatars = _itemLogic.resolveFavorites(_faveRepo.loadRecentFavorites(
            memberIds, ShopData.TOP_ITEM_COUNT * 2, Item.AVATAR));
        data.topAvatars = (avatars.size() <= ShopData.TOP_ITEM_COUNT) ? avatars
            : CollectionUtil.selectRandomSubset(avatars, ShopData.TOP_ITEM_COUNT);
        List<ListingCard> furniture = _itemLogic.resolveFavorites(_faveRepo.loadRecentFavorites(
            memberIds, ShopData.TOP_ITEM_COUNT * 2, Item.FURNITURE));
        data.topFurniture = (furniture.size() <= ShopData.TOP_ITEM_COUNT) ? furniture
            : CollectionUtil.selectRandomSubset(furniture, ShopData.TOP_ITEM_COUNT);

        // choose random 1 of 5 recent favorite pets & toys
        List<ListingCard> pets = _itemLogic.resolveFavorites(_faveRepo.loadRecentFavorites(
            memberIds, ShopData.TOP_ITEM_COUNT, Item.PET));
        data.featuredPet = (pets.size() > 0) ? RandomUtil.pickRandom(pets) : null;
        List<ListingCard> toys = _itemLogic.resolveFavorites(_faveRepo.loadRecentFavorites(
            memberIds, ShopData.TOP_ITEM_COUNT, Item.TOY));
        data.featuredToy = (toys.size() > 0) ? RandomUtil.pickRandom(toys) : null;

        // resolve the creator names for these listings
        List<ListingCard> allCards = Lists.newArrayList();
        allCards.addAll(data.topAvatars);
        allCards.addAll(data.topFurniture);
        if (data.featuredPet != null) {
            allCards.add(data.featuredPet);
        }
        if (data.featuredToy != null) {
            allCards.add(data.featuredToy);
        }
        _itemLogic.resolveCardNames(allCards);

        return data;
    }

    // from interface CatalogService
    public CatalogResult loadCatalog (
        CatalogQuery query, int offset, int rows, boolean includeCount)
        throws ServiceException
    {
        MemberRecord mrec = getAuthedUser();
        CatalogResult result = new CatalogResult();
        List<ListingCard> list = Lists.newArrayList();

        // if the type in question is not salable, return an empty list
        if (!isSalable(query.itemType)) {
            result.listings = list;
            return result;
        }

        ItemRepository<ItemRecord> repo = _itemLogic.getRepository(query.itemType);
        int tagId = (query.tag != null) ? repo.getTagRepository().getTagId(query.tag) : 0;

        // build our word search once and share it for loadCatalog() and countListings()
        ItemRepository<ItemRecord>.WordSearch context = repo.buildWordSearch(query.search);

        // fetch catalog records and loop over them
        list.addAll(Lists.transform(repo.loadCatalog(query.sortBy, showMature(mrec), context, tagId,
                                                     query.creatorId, null, 0, offset, rows),
                                    CatalogRecord.TO_CARD));

        // resolve the creator names for these listings
        _itemLogic.resolveCardNames(list);

        // if they want the total number of matches, compute that as well
        if (includeCount) {
            result.listingCount = repo.countListings(
                showMature(mrec), context, tagId, query.creatorId, null);
        }
        result.listings = list;

        // log this for posterity
        final int memberId = (mrec != null) ? mrec.memberId : MsoyEventLogger.UNKNOWN_MEMBER_ID;
        final String tracker = (mrec != null) ? mrec.visitorId : getVisitorTracker();
        _eventLog.shopPageBrowsed(memberId, tracker);

        return result;
    }

    // from interface CatalogService
    public PurchaseResult<Item> purchaseItem (
        byte itemType, int catalogId, Currency currency, int authedCost, String memories)
        throws ServiceException
    {
        final MemberRecord mrec = requireAuthedUser();

        BuyResult<Item> result = _catalogLogic.purchaseItem(
            mrec, itemType, catalogId, currency, authedCost, memories);
        PurchaseResult<Item> presult = result.toPurchaseResult();

        // update stats, not letting any booch in here screw with the purchase..
        try {
            boolean magicFree = result.wasMagicFreeBuy();
            MoneyTransaction memberTx = result.getMemberTransaction();
            List<MoneyTransaction> creatorTxs = result.getCreatorTransactions();
            if (!magicFree && creatorTxs != null) {
                for (MoneyTransaction creatorTx : creatorTxs) {
                    int creatorId = creatorTx.memberId;
                    if (mrec.memberId != creatorId && creatorTx.amount > 0) {
                        if (creatorTx.currency == Currency.COINS) {
                            _statLogic.incrementStat(
                                creatorId, StatType.COINS_EARNED_SELLING, creatorTx.amount);
                        }
                        // else: I guess if they earned BLING, that's it's own reward
    
                        // Some items have a stat that may need updating
                        if (itemType == Item.AVATAR) {
                            _statLogic.ensureIntStatMinimum(
                                creatorId, StatType.AVATARS_CREATED, StatType.ITEM_SOLD);
                        } else if (itemType == Item.FURNITURE) {
                            _statLogic.ensureIntStatMinimum(
                                creatorId, StatType.FURNITURE_CREATED, StatType.ITEM_SOLD);
                        } else if (itemType == Item.DECOR) {
                            _statLogic.ensureIntStatMinimum(
                                creatorId, StatType.BACKDROPS_CREATED, StatType.ITEM_SOLD);
                        }
                    }
                }
            }

            // update their stat set, if they aren't buying something from themselves.
            if (!magicFree && (mrec.memberId != presult.ware.creatorId) &&
                (memberTx.currency == Currency.COINS)) {
                _statLogic.incrementStat(mrec.memberId, StatType.COINS_SPENT, -memberTx.amount);
            }

        } catch (Exception e) {
            log.warning("Error logging stats during item purchase", e);
        }

        // if a charity was selected, set charity info
        if (result.getCharityTransaction() != null) {
            presult.charityPercentage = _runtime.money.charityPercentage;
            presult.charity = _memberRepo.loadMemberName(result.getCharityTransaction().memberId);
        }
        return presult;
    }

    // from interface CatalogService
    public int listItem (ItemIdent item, byte rating, int pricing, int salesTarget,
                         Currency currency, int cost, int basisCatalogId)
        throws ServiceException
    {
        final MemberRecord mrec = requireRegisteredUser();

        // validate the listing cost
        if (!currency.isValidCost(cost)) {
            log.warning("Requested to list an item for invalid price",
                        "item", item, "currency", currency, "cost", cost);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }

        // make sure they didn't hack their client and violate the pricing minimums
        int minPrice = ItemPrices.getMinimumPrice(currency, item.type, rating);
        if (cost < minPrice) {
            log.warning("Requested to price an item too low", "who", mrec.who(), "item", item,
                        "rating", rating, "price", cost, "minPrice", minPrice);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }

        // charities cannot list items in bars
        if (currency == Currency.BARS) {
            CharityRecord charityRec = _memberRepo.getCharityRecord(mrec.memberId);
            if (charityRec != null) {
                throw new ServiceException(ItemCodes.E_CHARITIES_CANNOT_LIST_FOR_BARS);
            }
        }

        // load a copy of the original item
        final ItemRepository<ItemRecord> repo = _itemLogic.getRepository(item.type);
        ItemRecord originalItem = repo.loadOriginalItem(item.itemId);
        if (originalItem == null) {
            log.warning("Can't find item to list", "item", item);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }

        // validate the basis
        if (basisCatalogId != 0) {
            if (!_itemLogic.isSuitableBasis(originalItem, basisCatalogId, currency, cost)) {
                throw new ServiceException(ItemCodes.E_BASIS_ERROR);
            }
        }

        // sanitize the sales target
        salesTarget = Math.max(salesTarget, CatalogListing.MIN_SALES_TARGET);

        // make sure we own AND created this item
        requireIsUser(mrec, originalItem.ownerId, "listItem", originalItem);
        requireIsUser(mrec, originalItem.creatorId, "listItem", originalItem);

        // make sure this item is not already listed
        if (originalItem.catalogId != 0) {
            log.warning("Requested to list already listed item", "who", mrec.who(), "item", item);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }

        // we will modify the original item (it's a clone, no need to worry) to create the new
        // catalog listing master item
        final int originalItemId = originalItem.itemId;
        final ItemRecord master = originalItem;
        master.prepareForListing(null);

        // process the payment of the listing fee and create the listing if it succeeds
        final long now = System.currentTimeMillis();
        final Currency fcurrency = currency;
        final int fpricing = pricing, fsalesTarget = salesTarget, fcost = cost;
        final int fbasisId = basisCatalogId;
        // the coin minimum price is the listing fee
        int listFee = ItemPrices.getMinimumPrice(Currency.COINS, item.type, rating);

        int catalogId = _moneyLogic.listItem(
            mrec, listFee, master.name, new MoneyLogic.BuyOperation<Integer>() {
            public Integer create (boolean magicFree, Currency currency, int amountPaid) {
                // create our new immutable catalog master item
                repo.insertOriginalItem(master, true);
                // create and insert the catalog record, return its new id
                return repo.insertListing(master, originalItemId, fpricing, fsalesTarget,
                                          fcurrency, fcost, now, fbasisId);
            }
        }).toPurchaseResult().ware;

        // copy tags from the original item to the new listing item
        repo.getTagRepository().copyTags(originalItemId, master.itemId, mrec.memberId, now);

        // apply the first rating
        repo.getRatingRepository().rate(master.itemId, mrec.memberId, rating);

        // note in the user action system that they listed an item
        _userActionRepo.logUserAction(UserAction.listedItem(mrec.memberId));

        // publish to the member's feed if it's not hidden
        if (pricing != CatalogListing.PRICING_HIDDEN) {
            _feedRepo.publishMemberMessage(
                mrec.memberId, FeedMessageType.FRIEND_LISTED_ITEM, master.name + "\t" +
                String.valueOf(repo.getItemType()) + "\t" + catalogId + "\t" +
                MediaDesc.mdToString(master.getThumbMediaDesc()));
        }

        // some items are related to a stat that may need updating.  Use originalItem.creatorId
        // so that agents and admins don't get credit for listing someone elses stuff.
        if (item.type == Item.AVATAR) {
            _statLogic.ensureIntStatMinimum(
                originalItem.creatorId, StatType.AVATARS_CREATED, StatType.ITEM_LISTED);
        } else if (item.type == Item.FURNITURE) {
            _statLogic.ensureIntStatMinimum(
                originalItem.creatorId, StatType.FURNITURE_CREATED, StatType.ITEM_LISTED);
        } else if (item.type == Item.DECOR) {
            _statLogic.ensureIntStatMinimum(
                originalItem.creatorId, StatType.BACKDROPS_CREATED, StatType.ITEM_LISTED);
        }

        // note that the listed item was created
        _itemLogic.itemUpdated(null, master);

        // note in the event log that an item was listed
        _eventLog.itemListedInCatalog(master.creatorId, mrec.visitorId, master.getType(),
            master.itemId, currency, cost, pricing, salesTarget);

        return catalogId;
    }

    // from interface CatalogServlet
    public CatalogListing loadListing (byte itemType, int catalogId, boolean forDisplay)
        throws ServiceException
    {
        MemberRecord mrec = getAuthedUser();

        // load up the old catalog record
        CatalogRecord record = _itemLogic.requireListing(itemType, catalogId, true);

        // if we're not the creator of the listing (who has to download it to update it) do
        // some access control checks
        if (mrec == null || (record.item.creatorId != mrec.memberId && !mrec.isSupport())) {
            // if the type in question is not salable, reject the request
            if (!isSalable(itemType)) {
                throw new ServiceException(ItemCodes.E_ACCESS_DENIED);
            }

            // if this listing is not meant for general sale, no lookey
            if (record.pricing == CatalogListing.PRICING_HIDDEN) {
                throw new ServiceException(ItemCodes.E_ACCESS_DENIED);
            }
        }

        // load up the basis item if requested
        CatalogListing.BasisItem basis = null;
        if (forDisplay && record.basisId > 0) {
            CatalogRecord basisRec = 
                _itemLogic.getRepository(itemType).loadListing(record.basisId, true);
            if (basisRec != null) {
                basis = new CatalogListing.BasisItem();
                basis.catalogId = basisRec.catalogId;
                basis.name = basisRec.item.name;
                basis.creator = _memberRepo.loadMemberName(basisRec.item.creatorId);
            }
        }

        // load up to 5 derived items if requested
        CatalogListing.DerivedItem[] derivatives = null;
        if (forDisplay && record.derivationCount > 0) {
            derivatives = _itemLogic.loadDerivedItems(itemType, catalogId, 5);
        }

        // secure the current price of the item for this member
        PriceQuote quote = _moneyLogic.securePrice((mrec == null) ? 0 : mrec.memberId,
            new CatalogIdent(itemType, catalogId), record.currency, record.cost);

        if (mrec != null) {
            // if this item is for sale for coins and this member is in a game, we may also want to
            // flush their pending coin earnings to ensure that they can buy it if affording it
            // requires a combination of their real coin balance plus their pending earnings
            if (record.currency == Currency.COINS) {
                _gameLogic.maybeFlushCoinEarnings(mrec.memberId, record.cost);
            }
        }

        // finally convert the listing to a runtime record
        CatalogListing clrec = record.toListing();
        clrec.detail.creator = _memberRepo.loadMemberName(record.item.creatorId);
        clrec.detail.memberItemInfo = _itemLogic.getMemberItemInfo(mrec, record.item.toItem());
        clrec.quote = quote;
        clrec.basis = basis;
        clrec.derivatives = derivatives;

        // let's remember this
        final int memberId = (mrec != null) ? mrec.memberId : MsoyEventLogger.UNKNOWN_MEMBER_ID;
        final String tracker = (mrec != null) ? mrec.visitorId : getVisitorTracker();
        _eventLog.shopDetailsViewed(memberId, tracker);

        return clrec;
    }

    public CatalogListing.DerivedItem[] loadAllDerivedItems (byte itemType, int catalogId)
        throws ServiceException
    {
        return _itemLogic.loadDerivedItems(itemType, catalogId, 0);
    }

    // from interface CatalogService
    public void updateListing (ItemIdent item)
        throws ServiceException
    {
        MemberRecord mrec = requireRegisteredUser();

        // load a copy of the original item
        ItemRepository<ItemRecord> repo = _itemLogic.getRepository(item.type);
        ItemRecord originalItem = repo.loadOriginalItem(item.itemId);
        if (originalItem == null) {
            log.warning("Can't find item for listing update", "item", item);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }

        // make sure we own this item
        requireIsUser(mrec, originalItem.ownerId, "updateListing", originalItem);

        // load up the old catalog record
        CatalogRecord record = repo.loadListing(originalItem.catalogId, false);
        if (record == null) {
            log.warning("Missing listing for update", "who", mrec.who(), "item", item,
                "catId", originalItem.catalogId);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }

        // load up the old list item
        ItemRecord oldListItem = repo.loadItem(record.listedItemId);

        // we will modify the original item (it's a clone, no need to worry) to create the new
        // catalog master item
        ItemRecord master = originalItem;
        master.prepareForListing(oldListItem);

        // update our catalog master item
        repo.updateOriginalItem(master);

        // note that the listed item was updated
        _itemLogic.itemUpdated(oldListItem, master);
    }

    // from interface CatalogService
    public void updatePricing (byte itemType, int catalogId, int pricing, int salesTarget,
                               Currency currency, int cost)
        throws ServiceException
    {
        MemberRecord mrec = requireRegisteredUser();

        // validate the listing cost
        if (!currency.isValidCost(cost)) {
            log.warning("Requested to update listing with an invalid price", "type", itemType,
                        "catId", catalogId, "currency", currency.toString(), "cost", cost);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }

        // load up the listing we're updating
        ItemRepository<ItemRecord> repo = _itemLogic.getRepository(itemType);
        CatalogRecord record = repo.loadListing(catalogId, true);
        if (record == null) {
            log.warning("Missing listing for update", "who", mrec.who(), "type", itemType,
                "catId", catalogId);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }

        // make sure they didn't hack their client and violate the pricing minimums
        if (cost < ItemPrices.getMinimumPrice(currency, itemType, (byte)record.item.getRating())) {
            log.warning("Requested to price an item too low", "who", mrec.who(), "type", itemType,
                        "catId", catalogId, "rating", record.item.getRating(), "cost", cost);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }

        // check basis item validity (in case of client hackery)
        if (record.basisId != 0) {
            if (!_itemLogic.isSuitableBasis(record.item, record.basisId, currency, cost)) {
                throw new ServiceException(ItemCodes.E_BASIS_ERROR);
            }
        }

        // load a copy of the original item
        ItemRecord originalItem = repo.loadOriginalItem(record.originalItemId);
        if (originalItem == null) {
            log.warning("Can't find original for pricing update", "who", mrec.who(),
                "type", itemType, "catId", catalogId, "itemId", record.originalItemId);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }

        // make sure we own this item
        requireIsUser(mrec, originalItem.ownerId, "updatePricing", originalItem);

        // sanitize the sales target
        salesTarget = Math.max(salesTarget, CatalogListing.MIN_SALES_TARGET);

        // get rid of derived listings
        // TODO: attribution phase II - update derived prices if there is a price change
        _itemLogic.removeDerivedListings(mrec, record, false);

        // now we can update the record
        repo.updatePricing(catalogId, pricing, salesTarget, currency, cost,
                           System.currentTimeMillis());
    }

    // from interface CatalogService
    public void removeListing (byte itemType, int catalogId)
        throws ServiceException
    {
        _itemLogic.removeListing(requireRegisteredUser(), itemType, catalogId);
    }

    // from interface CatalogService
    public Map<String, Integer> getPopularTags (byte type, int rows)
        throws ServiceException
    {
        ItemRepository<ItemRecord> repo = _itemLogic.getRepository(type);
        Map<String, Integer> result = Maps.newHashMap();
        for (TagPopularityRecord record : repo.getTagRepository().getPopularTags(rows)) {
            result.put(record.tag, record.count);
        }
        return result;
    }

    // from interface CatalogService
    public FavoritesResult loadFavorites (int memberId, byte itemType)
        throws ServiceException
    {
        FavoritesResult result = new FavoritesResult();
        // look up the party in question, if they don't exist, return null
        result.noter = _memberRepo.loadMemberName(memberId);
        if (result.noter == null) {
            return null;
        }
        result.favorites = _itemLogic.resolveFavorites(_faveRepo.loadFavorites(memberId, itemType));
        return result;
    }

    // from interface CatalogService
    public List<ListingCard> loadPotentialBasisItems (byte itemType)
        throws ServiceException
    {
        MemberRecord mrec = requireAuthedUser();
        List<ListingCard> cards = Lists.newArrayList();
        for (CatalogRecord crec : _itemLogic.getRepository(itemType).loadCatalog(
            Lists.transform(_faveRepo.loadFavorites(mrec.memberId, itemType),
                new Function<FavoriteItemRecord, Integer>() {
                    public Integer apply (FavoriteItemRecord frec) {
                        return frec.catalogId;
                    }
                }))) {

            if (_itemLogic.isSuitableBasis(itemType, mrec.memberId, crec, null, 0)) {
                cards.add(crec.toListingCard());
            }
        }
        return cards;
    }

    // from interface CatalogService
    public SuiteResult loadSuite (byte itemType, int suiteId)
        throws ServiceException
    {
        // NOTE: this method is expensive as fuck, but we cache the results on the client and
        // viewing the game shop is an extremely important step on the path to paying us money, so
        // we take the pain and hurt the database to make the game shop maximally convenient; if
        // this turns out to be a big drain, we need to look into flagging a game as having or not
        // having each of the various subtypes (easier) and tagged types (much harder)

        // right now we only support game suites (where suiteId == gameId), oh the hackery
        GameInfoRecord grec = _mgameRepo.loadGame(suiteId);
        if (grec == null) {
            throw new ServiceException(ItemCodes.E_NO_SUCH_ITEM);
        }

        // configure the suite metadata
        SuiteResult info = new SuiteResult();
        info.name = grec.name;
        info.suiteId = suiteId;
        info.creatorId = grec.creatorId;
        info.suiteTag = grec.shopTag;
        info.listings = Lists.newArrayList();

        // load up all subitems of the master
        MemberRecord mrec = getAuthedUser();
        for (IdentGameItem sitem : grec.getSuiteTypes()) {
            if (!sitem.isSalable()) {
                continue;
            }
            ItemRepository<ItemRecord> srepo = _itemLogic.getRepository(sitem.getType());
            List<CatalogRecord> slist = srepo.loadCatalog(
                CatalogQuery.SORT_BY_LIST_DATE, showMature(mrec), null, 0,
                0, null, info.suiteId, 0, Short.MAX_VALUE);
            info.listings.addAll(Lists.transform(slist, CatalogRecord.TO_CARD));
        }

        if (info.suiteTag != null) {
            // all tag repositories share the same name to id mapping
            int tagId = _itemLogic.getRepository(Item.PET).getTagRepository().getTagId(
                info.suiteTag);
            if (tagId != 0) {
                for (byte tagType : SUITE_TAG_TYPES) {
                    ItemRepository<ItemRecord> trepo = _itemLogic.getRepository(tagType);
                    List<CatalogRecord> tlist = trepo.loadCatalog(
                        CatalogQuery.SORT_BY_LIST_DATE, showMature(mrec), null, tagId,
                        info.creatorId, null, 0, 0, Short.MAX_VALUE);
                    info.listings.addAll(Lists.transform(tlist, CatalogRecord.TO_CARD));
                }
            }
        }

        // resolve the creator names for these listings
        _itemLogic.resolveCardNames(info.listings);

        return info;
    }

    /**
     * Helper function for {@link #loadShopData}.
     */
    protected ListingCard[] loadTopItems (MemberRecord mrec, byte type)
        throws ServiceException
    {
        ItemRepository<ItemRecord> repo = _itemLogic.getRepository(type);
        List<ListingCard> cards = Lists.newArrayList();
        for (CatalogRecord crec : repo.loadCatalog(CatalogQuery.SORT_BY_NEW_AND_HOT,
            showMature(mrec), null, 0, 0, null, 0, 0, ShopData.TOP_ITEM_COUNT)) {
            cards.add(crec.toListingCard());
        }
        return cards.toArray(new ListingCard[cards.size()]);
    }

    /**
     * Returns true if the specified item type is salable, false if not.
     */
    protected boolean isSalable (byte itemType)
        throws ServiceException
    {
        try {
            return Item.getClassForType(itemType).newInstance().isSalable();
        } catch (Exception e) {
            log.warning("Failed to check salability", "type", itemType, e);
            throw new ServiceException(ItemCodes.INTERNAL_ERROR);
        }
    }

    /**
     * Ensures that the specified user or a support user is taking the requested action.
     */
    protected void requireIsUser (MemberRecord mrec, int targetId, String action, ItemRecord item)
        throws ServiceException
    {
        if (mrec == null || (mrec.memberId != targetId && !mrec.isSupport())) {
            String who = (mrec == null) ? "null" : mrec.who();
            String iid = (item == null) ? "null" : ""+item.itemId;
            log.warning("Access denied for catalog action", "who", who, "wanted", targetId,
                        "action", action, "item", iid);
            throw new ServiceException(ItemCodes.E_ACCESS_DENIED);
        }
    }

    protected boolean showMature (MemberRecord mrec)
    {
        return (mrec == null) ? false : mrec.isSet(MemberRecord.Flag.SHOW_MATURE);
    }

    // our dependencies
    @Inject protected CatalogLogic _catalogLogic;
    @Inject protected FavoritesRepository _faveRepo;
    @Inject protected FeedRepository _feedRepo;
    @Inject protected GameLogic _gameLogic;
    @Inject protected ItemLogic _itemLogic;
    @Inject protected MoneyLogic _moneyLogic;
    @Inject protected MsoyEventLogger _eventLog;
    @Inject protected MsoyGameRepository _mgameRepo;
    @Inject protected RuntimeConfig _runtime;
    @Inject protected StatLogic _statLogic;
    @Inject protected UserActionRepository _userActionRepo;

    /** Used by {@link #loadSuite}. */
    protected static final byte[] SUITE_TAG_TYPES = new byte[] {
        Item.AVATAR, Item.FURNITURE, Item.DECOR, Item.TOY,
        Item.PET, Item.PHOTO, Item.AUDIO, Item.VIDEO
    };
}
