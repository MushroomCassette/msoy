//
// $Id$

package com.threerings.msoy.notify.client {

import flash.utils.Dictionary;

import mx.containers.HBox;
import mx.containers.VBox;

import com.threerings.flex.CommandButton;

import com.threerings.msoy.ui.FloatingPanel;

import com.threerings.msoy.client.WorldContext;

import com.threerings.msoy.notify.data.Notification;

public class NotificationDisplay extends FloatingPanel
{
    public function NotificationDisplay (ctx :WorldContext)
    {
        super(ctx, ctx.xlate("notify", "t.notify"));
        _box.maxHeight = 250;
        _box.minWidth = 200;
        styleName = "notificationDisplay";
        showCloseButton = true;
    }

    public function addNotification (notif :Notification) :void
    {
        if (_showing[notif.id]) {
            return;
        }
        _showing[notif.id] = true;

        var subBox :VBox = new VBox();
        subBox.setStyle("left", 0);
        subBox.setStyle("right", 0);
        var closeBtn :CommandButton = new CommandButton();
        closeBtn.setStyle("right", 0);
        closeBtn.styleName = "closeButton";
        closeBtn.setCallback(closeNotification, [ subBox, notif.id ]);
        var closeBox :HBox = new HBox();
        closeBox.addChild(closeBtn);
        subBox.addChild(closeBox);
        subBox.addChild(notif.getDisplay());
        _box.addChild(subBox);
    }

    override public function close () :void
    {
        super.close();
        _ctx.getNotificationDirector().notificationPanelClosed();
    }

    override protected function createChildren () :void
    {
        super.createChildren();

        addChild(_box);
    }

    protected function closeNotification (subBox :VBox, id :int) :void
    {
        _box.removeChild(subBox);
        _ctx.getNotificationDirector().acknowledgeNotification(id);

        // close the whole shootin' match
        if (_box.numChildren == 0) {
            close();
        }
    }

    protected var _showing :Dictionary = new Dictionary();

    protected var _box :VBox = new VBox();
}
}
