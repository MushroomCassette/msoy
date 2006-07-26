package com.threerings.msoy.client {

import mx.core.ScrollPolicy;

import mx.containers.Canvas;

import com.threerings.mx.controls.CommandButton;

import com.threerings.presents.client.ClientEvent;
import com.threerings.presents.client.ClientEvent;
import com.threerings.presents.client.ClientAdapter;

import com.threerings.msoy.chat.client.ChatControl;

import com.threerings.msoy.data.MediaData;
import com.threerings.msoy.data.MsoyUserObject;

/**
 * The control bar: the main menu and global UI element across all scenes.
 */
public class ControlBar extends Canvas
{
    public function ControlBar (ctx :MsoyContext)
    {
        _ctx = ctx;

        var fn :Function = function (event :ClientEvent) :void {
            checkControls();
        };
        _ctx.getClient().addClientObserver(
            new ClientAdapter(fn, fn, null, null, null, null, fn));

        verticalScrollPolicy = ScrollPolicy.OFF;
        horizontalScrollPolicy = ScrollPolicy.OFF;

        width = 800;
        height = 59;

        checkControls();
    }

    /**
     * Check to see which controls the client should see.
     */
    protected function checkControls () :void
    {
        var user :MsoyUserObject = _ctx.getClientObject();
        var isMember :Boolean = (user != null) && !user.isGuest();
        if (numChildren > 0 && (isMember == _isMember)) {
            return;
        }

        // remove all children
        while (numChildren > 0) {
            removeChildAt(0);
        }

        if (isMember) {
            [Embed(source="../../../../../../rsrc/media/uibar.png")]
            var cls :Class;
            setStyle("backgroundImage", cls);

            var chatControl :ChatControl = new ChatControl(_ctx);
            chatControl.x = 10;
            chatControl.y = 10;
            addChild(chatControl);

            // set up buttons
            var friendsBtn :CommandButton = new CommandButton();
            friendsBtn.setCommand(MsoyController.SHOW_FRIENDS, friendsBtn);
            friendsBtn.toggle = true;

            // TODO: dynamic layout?
            friendsBtn.x = 585;
            friendsBtn.y = 0;
            friendsBtn.width = 38;
            friendsBtn.height = 59;
            addChild(friendsBtn);

        } else {
            setStyle("backgroundImage", null);
            var logonPanel :LogonPanel = new LogonPanel(_ctx);
            logonPanel.x = 10;
            logonPanel.y = 10;
            addChild(logonPanel);
        }

        // and remember how things are set for now
        _isMember = isMember;
    }

    /** Our clientside context. */
    protected var _ctx :MsoyContext;

    /** Are we currently configured to show the controls for a member? */
    protected var _isMember :Boolean;
}
}
