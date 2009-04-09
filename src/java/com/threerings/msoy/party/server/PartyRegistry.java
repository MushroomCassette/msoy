//
// $Id$

package com.threerings.msoy.party.server;

import java.util.TreeMap;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import com.samskivert.jdbc.RepositoryUnit;

import com.samskivert.util.Comparators;
import com.samskivert.util.IntMap;
import com.samskivert.util.IntMaps;
import com.samskivert.util.Invoker;
import com.samskivert.util.RandomUtil;
import com.samskivert.util.StringUtil;
import com.samskivert.util.Tuple;

import com.threerings.presents.annotation.MainInvoker;
import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.data.InvocationCodes;
import com.threerings.presents.dobj.RootDObjectManager;
import com.threerings.presents.peer.data.NodeObject;
import com.threerings.presents.server.ClientManager;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.net.ConnectionManager;
import com.threerings.presents.server.InvocationManager;
import com.threerings.presents.util.PersistingUnit;

import com.threerings.crowd.server.BodyManager;
import com.threerings.crowd.server.PlaceManager;
import com.threerings.crowd.server.PlaceRegistry;
import com.threerings.whirled.data.ScenePlace;

import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.data.MsoyCodes;
import com.threerings.msoy.data.all.MediaDesc;
import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.server.AuxSessionFactory;
import com.threerings.msoy.server.MemberLocal;
import com.threerings.msoy.server.ServerConfig;

import com.threerings.msoy.group.data.all.Group;
import com.threerings.msoy.group.data.all.GroupMembership;
import com.threerings.msoy.group.server.persist.GroupRecord;
import com.threerings.msoy.group.server.persist.GroupRepository;

import com.threerings.msoy.notify.data.PartyInviteNotification;
import com.threerings.msoy.notify.server.NotificationManager;

import com.threerings.msoy.peer.data.MsoyNodeObject;
import com.threerings.msoy.peer.server.MsoyPeerManager;

import com.threerings.msoy.room.server.RoomManager;

import com.threerings.msoy.party.client.PartyBoardService;
import com.threerings.msoy.party.data.PartyAuthName;
import com.threerings.msoy.party.data.PartyBoardInfo;
import com.threerings.msoy.party.data.PartyCodes;
import com.threerings.msoy.party.data.PartyCredentials;
import com.threerings.msoy.party.data.PartyDetail;
import com.threerings.msoy.party.data.PartyInfo;
import com.threerings.msoy.party.data.PartyObject;
import com.threerings.msoy.party.data.PartySummary;

import static com.threerings.msoy.Log.log;

@Singleton
public class PartyRegistry
    implements PartyBoardProvider, PeerPartyProvider
{
    @Inject public PartyRegistry (InvocationManager invmgr)
    {
        invmgr.registerDispatcher(new PartyBoardDispatcher(this), MsoyCodes.WORLD_GROUP);
    }

    /**
     * Called to initialize the PartyRegistry after server startup.
     */
    public void init ()
    {
        ((MsoyNodeObject) _peerMgr.getNodeObject()).setPeerPartyService(
            _invmgr.registerDispatcher(new PeerPartyDispatcher(this)));
    }

    /**
     * Called during server initialization to give us a chance to wire up our authenticator and
     * session factory.
     */
    public void configSessionFactory (ConnectionManager conmgr, ClientManager clmgr)
    {
        conmgr.addChainedAuthenticator(_injector.getInstance(PartyAuthenticator.class));
        clmgr.setSessionFactory(new AuxSessionFactory(
            clmgr.getSessionFactory(), PartyCredentials.class, PartyAuthName.class,
            PartySession.class, PartyClientResolver.class));
    }

    /**
     * Returns the manager for the specified party or null.
     */
    public PartyManager getPartyManager (int partyId)
    {
        return _parties.get(partyId);
    }

    /**
     * Called on the server that hosts the passed-in player, not necessarily on the server
     * hosting the party.
     */
    public void issueInvite (MemberObject member, MemberName inviter, int partyId, String partyName)
    {
        _notifyMan.notify(member, new PartyInviteNotification(inviter, partyId, partyName));
    }

    /**
     * Called when the specified member has joined or left a party. This is called on the node on
     * which the member has a world session.
     */
    public void updateMemberParty (MemberObject memobj, PartySummary party)
    {
        // if the member is in a room, update their party info in the room
        PlaceManager placeMan = _placeReg.getPlaceManager(memobj.getPlaceOid());
        if (placeMan instanceof RoomManager) {
            if (party == null) {
                ((RoomManager)placeMan).memberLeftParty(memobj, memobj.partyId);
            } else {
                ((RoomManager)placeMan).memberJoinedParty(memobj, party);
            }
        }
        // also update the member's local bits
        memobj.getLocal(MemberLocal.class).updateParty(memobj, party);
    }

    /**
     * Returns the group id of the specified party or 0 if the party does not exist.
     */
    public int getPartyGroupId (int partyId)
    {
        PartyManager mgr = _parties.get(partyId);
        return (mgr == null) ? 0 : mgr.getPartyObject().group.getGroupId();
    }

    /**
     * Requests that the supplied member pre-join the specified party. If the method returns
     * normally, the player will have been added to the specified party.
     *
     * @throws InvocationException if the party cannot be joined for some reason.
     */
    public void preJoinParty (MemberName name, int partyId, byte rank)
        throws InvocationException
    {
        PartyManager mgr = _parties.get(partyId);
        if (mgr == null) {
            throw new InvocationException(PartyCodes.E_NO_SUCH_PARTY);
        }
        mgr.addPlayer(name, rank);
    }

    // from PartyBoardProvider
    public void locateParty (ClientObject co, final int partyId, PartyBoardService.JoinListener jl)
        throws InvocationException
    {
        String pnode = _peerMgr.lookupNodeDatum(new Function<NodeObject, String>() {
            public String apply (NodeObject nobj) {
                return ((MsoyNodeObject)nobj).hostedParties.containsKey(partyId)
                    ? nobj.nodeName : null;
            }
        });
        if (pnode == null) {
            throw new InvocationException(PartyCodes.E_NO_SUCH_PARTY);
        }
        jl.foundParty(partyId, _peerMgr.getPeerPublicHostName(pnode), _peerMgr.getPeerPort(pnode));
    }

    // from PartyBoardProvider
    public void getPartyBoard (
        ClientObject caller, String query, final InvocationService.ResultListener rl)
        throws InvocationException
    {
        final MemberObject member = (MemberObject)caller;

        // add every party we have access to to a collection sorted by score
        // Note: maybe we use a KeyValue, and create a top-N data structure that only even
        // saves the top-N candidates
        final TreeMap<PartySort,PartyInfo> visParties = Maps.newTreeMap();
        _peerMgr.applyToNodes(new Function<NodeObject,Void>() {
            public Void apply (NodeObject node) {
                for (PartyInfo info : ((MsoyNodeObject) node).hostedParties) {
                    // TODO: We could actually show parties in which you have an invitation,
                    // and or a leader invite...
                    if (info.isVisible(member)) {
                        visParties.put(computePartySort(info, member), info);
                    }
                }
                return null; // Void
            }
        });

        final IntMap<MediaDesc> icons = IntMaps.newHashIntMap();
        final List<PartyBoardInfo> results = Lists.newArrayList(Iterables.transform(
            Iterables.limit(visParties.values(), PARTIES_PER_BOARD),
            new Function<PartyInfo,PartyBoardInfo>() {
                public PartyBoardInfo apply (PartyInfo info) {
                    icons.put(info.groupId, null);
                    return new PartyBoardInfo(info);
                }
            }));

        _invoker.postUnit(new PersistingUnit("loadPartyGroup", rl) {
            @Override public void invokePersistent () throws Exception {
                for (GroupRecord rec : _groupRepo.loadGroups(icons.keySet())) {
                    icons.put(rec.groupId, rec.toLogo());
                }
            }

            @Override public void handleSuccess () {
                for (PartyBoardInfo party : results) {
                    party.icon = icons.get(party.info.groupId);
                }
                rl.requestProcessed(results);
            }
        });
    }

    // from PartyBoardProvider
    public void createParty (
        ClientObject caller, final String name, final int groupId, final boolean inviteAllFriends,
        final PartyBoardService.JoinListener jl)
        throws InvocationException
    {
        final MemberObject member = (MemberObject)caller;

        if (member.partyId != 0) {
            // TODO: possibly a better error? Surely this will be blocked on the client
            throw new InvocationException(InvocationCodes.E_INTERNAL_ERROR);
        }
        // verify that the user is at least a member of the specified group
        final GroupMembership groupInfo = member.groups.get(groupId);
        if (groupInfo == null) {
            throw new InvocationException(InvocationCodes.E_INTERNAL_ERROR); // shouldn't happen
        }

        _invoker.postUnit(new RepositoryUnit("loadPartyGroup") {
            public void invokePersist () throws Exception {
                _group = _groupRepo.loadGroup(groupId);
            }
            public void handleSuccess () {
                finishCreateParty(member, name, _group, groupInfo, inviteAllFriends, jl);
            }
            protected GroupRecord _group;
        });
    }

    // from PartyBoardProvider & PeerPartyProvider
    public void getPartyDetail (ClientObject caller, final int partyId,
                                final InvocationService.ResultListener rl)
        throws InvocationException
    {
        // see if we can handle it locally
        PartyManager mgr = _parties.get(partyId);
        if (mgr != null) {
            final PartyDetail detail = mgr.getPartyDetail();
            _invoker.postUnit(new RepositoryUnit("loadPartyGroup") {
                public void invokePersist () throws Exception {
                    GroupRecord rec = _groupRepo.loadGroup(detail.info.groupId);
                    detail.groupName = rec.name;
                    detail.icon = rec.toLogo();
                }

                public void handleSuccess () {
                    rl.requestProcessed(detail);
                }
            });
            return;
        }

        // otherwise ship it off to the node that handles it
        int sent = _peerMgr.invokeOnNodes(new Function<Tuple<Client,NodeObject>,Boolean>() {
            public Boolean apply (Tuple<Client,NodeObject> clinode) {
                MsoyNodeObject mnode = (MsoyNodeObject)clinode.right;
                if (!mnode.hostedParties.containsKey(partyId)) {
                    return false;
                }
                mnode.peerPartyService.getPartyDetail(clinode.left, partyId, rl);
                return true;
            }
        });
        if (sent == 0) {
            throw new InvocationException(PartyCodes.E_NO_SUCH_PARTY);
        }
    }

    /**
     * Called by a PartyManager when it's removed.
     */
    void partyWasRemoved (int partyId)
    {
        _parties.remove(partyId);
    }

    /**
     * Finish creating a new party.
     */
    protected void finishCreateParty (MemberObject member, String name, GroupRecord group,
                                      GroupMembership groupInfo, boolean inviteAllFriends,
                                      PartyBoardService.JoinListener jl)
    {
        PartyObject pobj = null;
        PartyManager mgr = null;
        try {
            // validate that they can create the party with this group
            if ((group.partyPerms == Group.PERM_MANAGER) &&
                    (groupInfo.rank < GroupMembership.RANK_MANAGER)) {
                jl.requestFailed(PartyCodes.E_GROUP_MGR_REQUIRED);
                return;
            }

            // set up the new PartyObject
            pobj = _omgr.registerObject(new PartyObject());
            pobj.id = _peerMgr.getNextPartyId();
            pobj.name = StringUtil.truncate(name, PartyCodes.MAX_NAME_LENGTH);
            pobj.group = groupInfo.group;
            pobj.icon = group.toLogo();
            pobj.leaderId = member.getMemberId();
            if (member.location instanceof ScenePlace) {
                pobj.sceneId = ((ScenePlace) member.location).sceneId;
            }

            // create the PartyManager and add the member
            mgr = _injector.getInstance(PartyManager.class);
            mgr.init(pobj, member.getMemberId());
            mgr.addPlayer(member.memberName, groupInfo.rank);

            // we're hosting this party so we send them to this same node
            jl.foundParty(pobj.id, ServerConfig.serverHost, ServerConfig.serverPorts[0]);

        } catch (Exception e) {
            log.warning("Problem creating party", e);
            if (e instanceof InvocationException) {
                jl.requestFailed(e.getMessage());
            } else {
                jl.requestFailed(InvocationCodes.E_INTERNAL_ERROR);
            }

            // kill the party object we created
            if (mgr != null) {
                mgr.shutdown();
            }
            if (pobj != null) {
                _omgr.destroyObject(pobj.getOid());
            }
            return;
        }

        // if we made it here, then register the party
        _parties.put(pobj.id, mgr);

        if (inviteAllFriends) {
            mgr.inviteAllFriends(member);
        }
    }

    /**
     * Compute the score for the specified party, or return null if the user
     * does not have access to it.
     */
    protected PartySort computePartySort (PartyInfo info, MemberObject member)
    {
        // start by giving every party a random score between 0 and 1
        float score = RandomUtil.rand.nextFloat();
        // add your rank in the group. (0, 1, or 2)
        score += member.getGroupRank(info.groupId);
        // add 3 if your friend is leading the party. (To make it more important than groups)
        if (member.isFriend(info.leaderId)) {
            score += 3;
        }
        // now, each party is in a "band" determined by group/friend, and then has a random
        // position within that band.
        return new PartySort(score, info.id);
    }

    /** Holds compared order between parties without having to recompute it for
     * every comparison. */
    protected static class PartySort
        implements Comparable<PartySort>
    {
        public PartySort (float score, int id)
        {
            _score = score;
            _id = id;
        }

        // NOTE: we do not implement equals or hashCode. We want every PartySort object to
        // only be equal to itself.

        // from Comparable
        public int compareTo (PartySort other)
        {
            // reverse the order, so that higher scores are first
            int cmp = Float.compare(other._score, _score);
            if (cmp == 0) {
                // but lower partyIds take priority
                cmp = Comparators.compare(_id, other._id);
            }
            return cmp;
        }

        protected float _score;
        protected int _id;
    } // end: class PartySort

    protected IntMap<PartyManager> _parties = IntMaps.newHashIntMap();

    protected static final int PARTIES_PER_BOARD = 10;

    @Inject protected @MainInvoker Invoker _invoker;
    @Inject protected BodyManager _bodyMan;
    @Inject protected GroupRepository _groupRepo;
    @Inject protected Injector _injector;
    @Inject protected InvocationManager _invmgr;
    @Inject protected MsoyPeerManager _peerMgr;
    @Inject protected NotificationManager _notifyMan;
    @Inject protected PlaceRegistry _placeReg;
    @Inject protected RootDObjectManager _omgr;
}
