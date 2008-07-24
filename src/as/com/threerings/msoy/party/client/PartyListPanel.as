//
// $Id: FriendsListPanel.as 9910 2008-07-18 23:24:40Z nathan $

package com.threerings.msoy.world.client {

import flash.events.Event;
import flash.events.FocusEvent;
import flash.events.KeyboardEvent;
import flash.events.MouseEvent;

import flash.geom.Point;
import flash.geom.Rectangle;

import flash.ui.Keyboard;

import flash.utils.Dictionary;

import mx.collections.ArrayCollection;
import mx.collections.Sort;

import mx.containers.VBox;
import mx.containers.TitleWindow;

import mx.controls.Label;
import mx.controls.List;
import mx.controls.TextInput;

import mx.core.ClassFactory;
import mx.core.ScrollPolicy;
import mx.core.mx_internal;

import mx.events.CloseEvent;
import mx.events.FlexEvent;

import mx.managers.PopUpManager;

import com.threerings.presents.client.InvocationAdapter;

import com.threerings.presents.dobj.AttributeChangedEvent;
import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryUpdatedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.SetListener;

import com.threerings.flex.PopUpUtil;

import com.threerings.util.Log;
import com.threerings.util.ValueEvent;

import com.threerings.msoy.client.DeploymentConfig;
import com.threerings.msoy.client.MemberService;
import com.threerings.msoy.client.Msgs;
import com.threerings.msoy.client.MsoyClient;

import com.threerings.msoy.data.MemberObject;

import com.threerings.msoy.data.all.PeerEntry;
import com.threerings.msoy.data.all.MemberName;

public class PartyListPanel extends TitleWindow
    implements SetListener, AttributeChangeListener
{
    /** The width of the popup, defined by the width of the header image. */
    public static const POPUP_WIDTH :int = 219;

    public function PartyListPanel (ctx :WorldContext) :void
    {
        _ctx = ctx;
        _ctx.getClient().addEventListener(MsoyClient.MINI_WILL_CHANGE, miniWillChange);

        addEventListener(CloseEvent.CLOSE, _ctx.getWorldController().handlePopPartyList);
    }

    public function show () :void
    {
        PopUpManager.addPopUp(this, _ctx.getTopPanel(), false);
        systemManager.addEventListener(Event.RESIZE, stageResized);
    }

    public function shutdown () :void
    {
        systemManager.removeEventListener(Event.RESIZE, stageResized);
        _ctx.getMemberObject().removeListener(this);
        PopUpManager.removePopUp(this);
    }

    public function memberObjectUpdated (memObj :MemberObject) :void
    {
        init(memObj);
    }

    // from SetListener
    public function entryAdded (event :EntryAddedEvent) :void
    {
        if (event.getName() == MemberObject.FRIENDS) {
            addFriend(event.getEntry() as PeerEntry);
        }
    }

    // from SetListener
    public function entryUpdated (event :EntryUpdatedEvent) :void
    {
        if (event.getName() == MemberObject.FRIENDS) {
            var newEntry :PeerEntry = event.getEntry() as PeerEntry;
            var oldEntry :PeerEntry = event.getOldEntry() as PeerEntry;
            removeFriend(oldEntry);
            //if (newEntry.online) {
                addFriend(newEntry);
            //}
        }
    }

    // from SetListener
    public function entryRemoved (event :EntryRemovedEvent) :void
    {
        if (event.getName() == MemberObject.FRIENDS) {
            removeFriend(event.getOldEntry() as PeerEntry);
        }
    }

    // from AttributeChangeListener
    public function attributeChanged (event :AttributeChangedEvent) :void
    {
        if (event.getName() == MemberObject.HEADLINE) {
            //setStatus(event.getValue() as String);
        }
    }

    override protected function createChildren () :void
    {
        super.createChildren();

        // Panel provides no way to customize this, other than overriding the class and blowing
        // away what it set these to.
        mx_internal::closeButton.explicitWidth = 13;
        mx_internal::closeButton.explicitHeight = 14;

        // styles and positioning
        styleName = "friendsListPanel";
        showCloseButton = true;
        width = POPUP_WIDTH;
        var placeBounds :Rectangle = _ctx.getTopPanel().getPlaceViewBounds(); 
        height = placeBounds.height - PADDING * 2;
        x = placeBounds.x + placeBounds.width - width - PADDING;
        y = placeBounds.y + PADDING;

        _friendsList = new List();
        _friendsList.styleName = "friendList";
        _friendsList.horizontalScrollPolicy = ScrollPolicy.OFF;
        // I'd love to make this AUTO, but labels are stupid and can't deal with growing only
        // up to a dynamic size, so I've got to make all the widths static.  
        _friendsList.verticalScrollPolicy = ScrollPolicy.ON;
        _friendsList.percentWidth = 100;
        _friendsList.percentHeight = 100;
        _friendsList.itemRenderer = new ClassFactory(FriendRenderer);
        _friendsList.dataProvider = _friends;
        _friendsList.selectable = false;
        _friendsList.variableRowHeight = true;
        addChild(_friendsList);

        // set up the sort for the collection
        var sort :Sort = new Sort();
        sort.compareFunction = sortFunction;
        _friends.sort = sort;
        _friends.refresh();

        // add a little separator
        var separator :VBox = new VBox();
        separator.percentWidth = 100;
        separator.height = 1;
        separator.styleName = "friendsListSeparator";
        addChild(separator);

        // add the little box at the bottom
        var box :VBox = new VBox();
        box.percentWidth = 100;
        box.styleName = "friendsListEditorBox";
        addChild(box);
    
        // initialize with currently online friends
        init(_ctx.getMemberObject());
    }

    override protected function layoutChrome (unscaledWidth :Number, unscaledHeight :Number) :void
    {
        super.layoutChrome(unscaledWidth, unscaledHeight);

        mx_internal::closeButton.x = POPUP_WIDTH - mx_internal::closeButton.width - 5;
        mx_internal::closeButton.y = 5;
    }

    protected function init (memObj :MemberObject) :void
    {
        memObj.addListener(this);

        var currentEntries :Array = _friends.toArray();
        for each (var friend :PeerEntry in memObj.friends.toArray()) {
            /*if (!friend.online) {
                continue;
            }*/

            if (!containsFriend(friend)) {
                addFriend(friend);
            } else {
                var original :Object = _originals[friend.getMemberId()];
                currentEntries.splice(currentEntries.indexOf(original), 1);
            }
        }

        // anything left in currentEntries wasn't found on the new MemberObject
        for each (var entry :Array in currentEntries) {
            removeFriend(entry[1] as PeerEntry);
        }
    }

    protected function sortFunction (o1 :Object, o2 :Object, fields :Array = null) :int
    {
        if (!(o1 is Array) || !(o2 is Array)) {
            return 0;
        }

        var friend1 :PeerEntry = (o1 as Array)[1] as PeerEntry;
        var friend2 :PeerEntry = (o2 as Array)[1] as PeerEntry;
        return MemberName.BY_DISPLAY_NAME(friend1.getName(), friend2.getName());
    }

    protected function containsFriend (friend :PeerEntry) :Boolean
    {
        return _originals[friend.getMemberId()] !== undefined;
    }

    protected function addFriend (friend :PeerEntry) :void
    {
        var data :Array = [ _ctx, friend ];
        _originals[friend.getMemberId()] = data;
        _friends.addItem(data);
    }

    protected function removeFriend (friend :PeerEntry) :void
    {
        var data :Array = _originals[friend.getMemberId()] as Array;
        if (data != null) {
            _friends.removeItemAt(_friends.getItemIndex(data));
        }
        delete _originals[friend.getMemberId()];
    }

    protected function stageResized (...ignored) :void
    {
        var placeBounds :Rectangle = _ctx.getTopPanel().getPlaceViewBounds(); 
        // fix the height
        height = placeBounds.height - PADDING * 2;
        // fit the popup within the new bounds, minux padding.
        placeBounds.x += PADDING;
        placeBounds.y += PADDING;
        placeBounds.width -= PADDING * 2;
        placeBounds.height -= PADDING * 2;
        PopUpUtil.fitInRect(this, placeBounds);
    }

    protected function miniWillChange (event :ValueEvent) :void
    {
        if (event.value) {
            _currentX = x;
        } else {
            x = _currentX;
        }
    }

    private static const log :Log = Log.getLog(PartyListPanel);

    protected static const PADDING :int = 10;

    /** Defined in Java as com.threerings.msoy.person.data.Profile.MAX_STATUS_LENGTH */
    protected static const PROFILE_MAX_STATUS_LENGTH :int = 100;

    protected var _ctx :WorldContext;
    protected var _friendsList :List;
    protected var _friends :ArrayCollection = new ArrayCollection();
    protected var _originals :Dictionary = new Dictionary();
    protected var _currentX :int = 0;
}
}
