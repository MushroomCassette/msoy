//
// $Id$

package com.threerings.msoy.client {

import flash.events.Event;
import flash.events.MouseEvent;
import flash.geom.Rectangle;
import flash.system.Capabilities;

import mx.core.Application;
import mx.core.Container;
import mx.core.ScrollPolicy;
import mx.core.UIComponent;

import mx.containers.Canvas;
import mx.containers.HBox;

import mx.controls.Label;
import mx.controls.scrollClasses.ScrollBar;

import com.threerings.util.ConfigValueSetEvent;
import com.threerings.util.Log;
import com.threerings.util.MessageBundle;
import com.threerings.util.MethodQueue;
import com.threerings.util.ValueEvent;

import com.threerings.crowd.client.PlaceView;
import com.threerings.crowd.client.LocationObserver;
import com.threerings.crowd.data.PlaceObject;

import com.threerings.parlor.game.data.GameObject;

import com.threerings.msoy.chat.client.ChatTabBar;
import com.threerings.msoy.chat.client.ChatOverlay;
import com.threerings.msoy.chat.client.ComicOverlay;
import com.threerings.msoy.chat.client.GameChatContainer;
import com.threerings.msoy.chat.client.MsoyChatDirector;

import com.threerings.msoy.world.client.AbstractRoomView;

public class TopPanel extends Canvas
    implements LocationObserver
{
    public static const DECORATIVE_MARGIN_HEIGHT :int = 0;

    public static const RIGHT_SIDEBAR_WIDTH :int = 300;

    /**
     * Construct the top panel.
     */
    public function TopPanel (ctx :MsoyContext, controlBar :ControlBar)
    {
        _ctx = ctx;
        _ctx.getLocationDirector().addLocationObserver(this);
        percentWidth = 100;
        percentHeight = 100;
        verticalScrollPolicy = ScrollPolicy.OFF;
        horizontalScrollPolicy = ScrollPolicy.OFF;
        styleName = "topPanel";

        var chatTabs :ChatTabBar = new ChatTabBar(_ctx);
        _ctx.getMsoyChatDirector().setChatTabs(chatTabs);

        if (!_ctx.getMsoyClient().isFeaturedPlaceView()) {
            _headerBar = new HeaderBar(_ctx, chatTabs);
            _headerBar.includeInLayout = false;
            _headerBar.setStyle("top", 0);
            _headerBar.setStyle("left", 0);
            _headerBar.setStyle("right", 0);
            addChild(_headerBar);
        }

        _placeBox = new PlaceBox();
        _placeBox.autoLayout = false;
        _placeBox.includeInLayout = false;
        addChild(_placeBox);

        if (!_ctx.getMsoyClient().isFeaturedPlaceView()) {
            // only create and display an overlay for real clients
            _comicOverlay = new ComicOverlay(_ctx, _placeBox);
            _ctx.getMsoyChatDirector().addChatDisplay(_comicOverlay);

            // set up the control bar
            _controlBar = controlBar;
            _controlBar.init(ctx, this);
            _controlBar.includeInLayout = false;
            _controlBar.setStyle("bottom", 0);
            _controlBar.setStyle("left", 0);
            _controlBar.setStyle("right", 0);
            addChild(_controlBar);
            _controlBar.setLocation(null, false, false);

            // show a subtle build-stamp
            var buildStamp :Label = new Label();
            buildStamp.includeInLayout = false;
            buildStamp.mouseEnabled = false;
            buildStamp.mouseChildren = false;
            buildStamp.text = "Build: " + DeploymentConfig.buildTime;
            buildStamp.setStyle("color", "#F7069A");
            buildStamp.setStyle("fontSize", 8);
            buildStamp.setStyle("bottom", ControlBar.HEIGHT);
            // The scrollbar isn't really this thick, but it's pretty close.
            buildStamp.setStyle("right", ScrollBar.THICKNESS);
            addChild(buildStamp);
        }

        // clear out the application and install ourselves as the only child
        var app :Application = Application(Application.application);
        app.removeAllChildren();
        app.addChild(this);

        app.stage.addEventListener(Event.RESIZE, stageResized);
        _ctx.getClient().addEventListener(MsoyClient.MINI_WILL_CHANGE, miniWillChange);

        // display something until someone comes along and sets a real view on us
        setPlaceView(new BlankPlaceView());
    }

    /**
     * Ensures that we are running a sufficiently new version of Flash, returning true if so. If
     * not, it displays a message to the user indicating that they need to upgrade their Flash
     * player and returns false.
     */
    public function verifyFlashVersion () :Boolean
    {
        // the version looks like "LNX 9,0,31,0"
        try {
            var bits :Array = Capabilities.version.split(" ");
            if (bits.length < 2) {
                throw new Error("Failed to split on space");
            }
            bits = (bits[1] as String).split(",");
            if (bits.length < 3) {
                throw new Error("Failed to split on comma");
            }

            // check the major and minor version numbers
            if (int(bits[0]) >= MIN_FLASH_VERSION && int(bits[2]) >= MIN_FLASH_REVISION) {
                return true;
            }

            // display an error and fail
            var panel :DisconnectedPanel = new DisconnectedPanel(_ctx);
            panel.setMessage(MessageBundle.tcompose(
                                 "m.min_flash_version", bits[0], bits[2],
                                 MIN_FLASH_VERSION, MIN_FLASH_REVISION), true);
            setPlaceView(panel);
            return false;

        } catch (error :Error) {
            trace("Choked checking version [version=" + Capabilities.version +
                  ", error=" + error + ".");
            // ah well, whatever, let 'em in and hope for the best
        }
        return true;
    }

    // from LocationObserver
    public function locationMayChange (placeId :int) :Boolean
    {
        return true;
    }

    // from LocationObserver
    public function locationDidChange (place :PlaceObject) :void
    {
        if (_controlBar != null) {
            _controlBar.locationDidChange(place);
        }
        if (place == null) {
            clearPlaceView(null);
        }
    }

    // from LocationObserver
    public function locationChangeFailed (placeId :int, reason :String) :void
    {
        // NOOP
    }

    /**
     * Get the flex container that is holding the PlaceView. This is useful if you want to overlay
     * things over the placeview or register to receive flex-specific events.
     */
    public function getPlaceContainer () :PlaceBox
    {
        return _placeBox;
    }

    /**
     * Returns the currently configured place view.
     */
    public function getPlaceView () :PlaceView
    {
        return _placeBox.getPlaceView();
    }

    /**
     * Returns the chat overlay that is in use, or null if there is none.
     */
    public function getChatOverlay () :ChatOverlay
    {
        if (_rightPanel is GameChatContainer) {
            return (_rightPanel as GameChatContainer).getChatOverlay();
        } else {
            return _comicOverlay;
        }
    }

    /**
     * Returns the comic overlay that is used for all place view chat
     */
    public function getPlaceChatOverlay () :ComicOverlay
    {
        return _comicOverlay;
    }

    /**
     * Sets the specified view as the current place view.
     */
    public function setPlaceView (view :PlaceView) :void
    {
        _placeBox.setPlaceView(view);
        layoutPanels();

        if (_comicOverlay != null) {
            _comicOverlay.displayChat(view is ChatPlaceView);
        }
    }

    /**
     * Clear the specified place view, or null to clear any.
     */
    public function clearPlaceView (view :PlaceView) :void
    {
        if (_placeBox.clearPlaceView(view)) {
            setPlaceView(new BlankPlaceView());
        }
    }

    /**
     * Returns the location and dimensions of the place view in relation to the entire stage.
     */
    public function getPlaceViewBounds () :Rectangle
    {
        var bounds :Rectangle = new Rectangle();
        bounds.x = 0;
        bounds.y = HeaderBar.HEIGHT;
        bounds.width = stage.stageWidth - getRightPanelWidth();
        if (_chat != null) {
            bounds.width -= _chatBounds.width + CHAT_PADDING;
        }
        bounds.height = stage.stageHeight - HeaderBar.HEIGHT;
        if (padVertical(_placeBox.getPlaceView())) {
            bounds.y += DECORATIVE_MARGIN_HEIGHT;
            bounds.height -= 2*DECORATIVE_MARGIN_HEIGHT;
        }
        bounds.height -= ControlBar.HEIGHT;
        return bounds;
    }

    /**
     * Returns a reference to our ControlBar component.
     */
    public function getControlBar () :ControlBar
    {
        return _controlBar;
    }

    /**
     * Returns a reference to our HeaderBar component
     */
    public function getHeaderBar () :HeaderBar
    {
        return _headerBar;
    }

    /**
     * Set the panel at the top of the display.
     *
     * This is an unfortunate name collision.  However, it is hte name that makes sense...
     */
    public function setTopPanel (top :UIComponent) :void
    {
        clearTopPanel();
        _topPanel = top;
        _topPanel.includeInLayout = false;
        addChild(_topPanel);
        layoutPanels();
    }

    public function clearTopPanel (top :UIComponent = null) :void
    {
        if ((_topPanel != null) && (top == null || top == _topPanel)) {
            if (_topPanel.parent == this) {
                removeChild(_topPanel);
            }
            _topPanel = null;
            layoutPanels();
        }
    }

    public function getTopPanel () :UIComponent
    {
        return _topPanel;
    }

    /**
     * Configures our right side panel. Any previous right side panel will be cleared.
     */
    public function setRightPanel (side :UIComponent) :void
    {
        clearRightPanel(null);
        _rightPanel = side;
        _rightPanel.includeInLayout = false;
        _rightPanel.width = side.width;
        addChild(_rightPanel);
        layoutPanels();
    }

    /**
     * Clear the specified side panel, or null to clear any.
     */
    public function clearRightPanel (side :UIComponent = null) :void
    {
        if ((_rightPanel != null) && (side == null || side == _rightPanel)) {
            if (_rightPanel.parent == this) {
                removeChild(_rightPanel);
            }
            _rightPanel = null;
            layoutPanels();
        }
    }

    public function getRightPanel () :UIComponent
    {
        return _rightPanel;
    }

    public function getRightPanelWidth () :int
    {
        return (_rightPanel == null ? 0 : _rightPanel.width);
    }

    public function isMinimized () :Boolean
    {
        return _minimized;
    }

    public function slideInChat (chat :Container, bounds :Rectangle) :void
    {
        _chatBounds = bounds;
        if (_chat != null || _ctx.getMsoyClient().isFeaturedPlaceView()) {
            // we already own the chat, or we shouldn't be showing slide chat.
            return;
        }

        chat.autoLayout = false;
        chat.includeInLayout = false;
        addChild(_chat = chat);
        layoutPanels();
    }

    public function slideOutChat () :void
    {
        if (_chat == null) {
            // we don't currently own the chat
            return;
        }

        removeChild(_chat);
        _chat = null;
        layoutPanels();
    }

    protected function stageResized (event :Event) :void
    {
        layoutPanels();
    }

    protected function miniWillChange (event :ValueEvent) :void
    {
        if (event.value as Boolean) {
            minimizePlaceView();
        } else {
            restorePlaceView();
        }
    }

    /**
     * Take care of any bits that need to be changed when the place view is getting mini'd.
     */
    protected function minimizePlaceView () :void
    {
        _minimized = true;

        _headerBar.miniChanged();
        _controlBar.miniChanged();
    }

    /**
     * Undo any bits that were changed in minimizePlaceView()
     */
    protected function restorePlaceView () :void
    {
        _minimized = false;

        _headerBar.miniChanged();
        _controlBar.miniChanged();
    }

    protected function getTopPanelHeight () :int
    {
        return _topPanel != null ? Math.round(_topPanel.height) : 0;
    }

    protected function layoutPanels () :void
    {
        if (_ctx.getMsoyClient().isFeaturedPlaceView()) {
            // in this case, we only have one panel...
            updatePlaceViewSize();
            return;
        }

        _controlBar.setStyle("left", 0);
        _headerBar.setStyle("left", 0);
        _headerBar.setStyle("top", getTopPanelHeight());

        if (_topPanel != null) {
            _topPanel.setStyle("top", 0);
            _topPanel.setStyle("right", 0);
            _topPanel.setStyle("left", 0);
        }

        if (_rightPanel != null) {
            _rightPanel.setStyle("top", getTopPanelHeight() + HeaderBar.HEIGHT);
            _rightPanel.setStyle("right", 0);
            _rightPanel.setStyle("bottom", ControlBar.HEIGHT);

            // if we have no place view currently, stretch it all the way to the left; otherwise
            // let it be as wide as it wants to be
            if (_placeBox.parent == this) {
                _rightPanel.clearStyle("left");
            } else {
                _rightPanel.setStyle("left", 0);
            }

            _controlBar.setSpacerWidth(stage.stageWidth - _rightPanel.width);
        } else {
            _controlBar.setSpacerWidth(0);
        }

        updatePlaceViewSize();
    }

    protected function updatePlaceViewSize () :void
    {
        if (_placeBox.parent != this) {
            return; // nothing doing if we're not in control
        }

        if (_ctx.getMsoyClient().isFeaturedPlaceView()) {
            _placeBox.clearStyle("top");
            _placeBox.clearStyle("bottom");
            _placeBox.clearStyle("left");
            _placeBox.clearStyle("right");
            _placeBox.setActualSize(stage.stageWidth, stage.stageHeight);
            return;
        }

        var w :int = stage.stageWidth - getRightPanelWidth();
        var h :int = stage.stageHeight - HeaderBar.HEIGHT - getTopPanelHeight();
        var top :int = HeaderBar.HEIGHT + getTopPanelHeight();
        if (padVertical(_placeBox.getPlaceView())) {
            top += DECORATIVE_MARGIN_HEIGHT;
            h -= DECORATIVE_MARGIN_HEIGHT;
        }

        var bottom :int = 0;
        // for place views, we want to insert decorative margins above and below the view
        if (padVertical(_placeBox.getPlaceView())) {
            bottom += DECORATIVE_MARGIN_HEIGHT;
            h -= DECORATIVE_MARGIN_HEIGHT;
        }
        bottom += ControlBar.HEIGHT;
        h -= ControlBar.HEIGHT;

        var left :int = 0;
        if (_chat != null) {
            left += _chatBounds.width + CHAT_PADDING;
            w -= _chatBounds.width + CHAT_PADDING;
            _chat.setStyle("top", top);
            _chat.setStyle("bottom", bottom);
            _chat.setStyle("left", 0);
            _chat.setStyle("right", getRightPanelWidth() + w + CHAT_PADDING);
            _chatBounds.height = h;
        }
        _comicOverlay.setTargetBounds(_chatBounds != null ? _chatBounds : 
            new Rectangle(0, 0, ChatOverlay.DEFAULT_WIDTH, h));
        _placeBox.setStyle("top", top);
        _placeBox.setStyle("bottom", bottom);
        _placeBox.setStyle("left", left);
        _placeBox.setStyle("right", getRightPanelWidth());
        _placeBox.setActualSize(w, h);
    }

    protected function padVertical (view :PlaceView) :Boolean
    {
        return (view is MsoyPlaceView) && (view as MsoyPlaceView).padVertical();
    }

    private static const log :Log = Log.getLog(TopPanel);

    protected static const CHAT_PADDING :int = 15;

    /** The giver of life. */
    protected var _ctx :MsoyContext;

    /** The box that will hold the placeview. */
    protected var _placeBox :PlaceBox;

    /** The current top panel component. */
    protected var _topPanel :UIComponent;

    /** The current right panel component. */
    protected var _rightPanel :UIComponent;

    /** Header bar at the top of the window. */
    protected var _headerBar :HeaderBar;

    /** Control bar at the bottom of the window. */
    protected var _controlBar :ControlBar;

    /** Storage for a GUI element corresponding to decorative lines. */
    protected var _decorativeBar :Canvas;

    /** A flag to indicate if we're working in mini-view or not. */
    protected var _minimized :Boolean = false;

    protected var _comicOverlay :ComicOverlay;

    /** When chat is operating in slide mode (non-overlay), we manage it here. */
    protected var _chat :Container;
    protected var _chatBounds :Rectangle;

    protected static const MIN_FLASH_VERSION :int = 9;
    protected static const MIN_FLASH_REVISION :int = 115;
}
}
