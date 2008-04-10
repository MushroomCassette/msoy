//
// $Id$

package com.threerings.msoy.chat.client {

import flash.display.BlendMode;
import flash.display.DisplayObject;
import flash.display.DisplayObjectContainer;
import flash.display.Graphics;
import flash.display.InteractiveObject;
import flash.display.Sprite;
import flash.display.Stage;

import flash.events.Event;
import flash.events.MouseEvent;

import flash.geom.Point;
import flash.geom.Rectangle;

import flash.text.TextFormat;

import flash.utils.getTimer; // function import

import mx.events.FlexEvent;
import mx.events.ResizeEvent;
import mx.events.ScrollEvent;

import mx.core.Container;
import mx.core.IRawChildrenContainer;
import mx.core.UIComponent;

import mx.controls.scrollClasses.ScrollBar;
import mx.controls.VScrollBar;

import com.threerings.util.ArrayUtil;
import com.threerings.util.ConfigValueSetEvent;
import com.threerings.util.HashMap;
import com.threerings.util.Log;
import com.threerings.util.MessageManager;
import com.threerings.util.Name;
import com.threerings.util.StringUtil;

import com.threerings.crowd.chat.data.ChatCodes;
import com.threerings.crowd.chat.data.ChatMessage;
import com.threerings.crowd.chat.data.SystemMessage;
import com.threerings.crowd.chat.data.TellFeedbackMessage;
import com.threerings.crowd.chat.data.UserMessage;

import com.threerings.flash.ColorUtil;

import com.threerings.whirled.spot.data.SpotCodes;

import com.threerings.msoy.chat.data.ChannelMessage;
import com.threerings.msoy.chat.data.ChatChannel;
import com.threerings.msoy.chat.data.TimedMessageDisplay;

import com.threerings.msoy.client.ControlBar;
import com.threerings.msoy.client.LayeredContainer;
import com.threerings.msoy.client.MsoyContext;
import com.threerings.msoy.client.PlaceBox;
import com.threerings.msoy.client.Prefs;
import com.threerings.msoy.data.MsoyCodes;
import com.threerings.msoy.data.VizMemberName;

import com.threerings.msoy.game.client.MsoyGamePanel;

import com.threerings.msoy.notify.data.NotifyMessage;

public class ChatOverlay
    implements TabbedChatDisplay
{
    public static const SCROLL_BAR_LEFT :int = 1;
    public static const SCROLL_BAR_RIGHT :int = 2;

    /** Pixel padding surrounding most things. */
    public static const PAD :int = 10;

    /**
     * Create the standard chat TextFormat. This is exposed so that other things can
     * show something in the current "chat font".
     */
    public static function createChatFormat () :TextFormat
    {
        var fmt :TextFormat = new TextFormat();
        fmt.font = FONT;
        fmt.size = Prefs.getChatFontSize();
        fmt.color = 0x000000;
        fmt.bold = false;
        fmt.underline = false;
        return fmt;
    }

    public function ChatOverlay (ctx :MsoyContext, target :LayeredContainer, 
        scrollBarSide :int = SCROLL_BAR_LEFT, includeOccupantList :Boolean = true)
    {
        _ctx = ctx;
        _msgMan = _ctx.getMessageManager();
        _includeOccList = includeOccupantList;
        _scrollBarSide = scrollBarSide;

        createStandardFormats();

        // overlay for history chat that may get pulled out and put on the side in slide chat mode
        _historyOverlay.mouseEnabled = false;
        _historyOverlay.blendMode = BlendMode.LAYER;

        _target = target;
        layout();
        displayChat(true);

        _closedTabs = new ExpiringSet(LOCALTYPE_EXPIRE_TIME, localtypeExpired);

        // listen for preferences changes, update history mode
        Prefs.config.addEventListener(ConfigValueSetEvent.CONFIG_VALUE_SET,
            handlePrefsUpdated, false, 0, true);
    }

    // from ChatDisplay
    public function clear () :void
    {
        clearGlyphs(_subtitles);
        clearGlyphs(_showingHistory);
    }

    // from ChatDisplay
    public function displayMessage (msg :ChatMessage, alreadyDisp :Boolean) :Boolean
    {
        log.debug("displayMessage [msg=" + msg.message + ", localtype=" + msg.localtype + 
            ", timestamp=" + msg.timestamp + ", type=" + getType(msg, false) + "]");
        var type :int = getType(msg, false);
        if (type == IGNORECHAT) {
            return false;
        }

        if (msg is SystemMessage || msg is NotifyMessage || msg.localtype == _localtype) {
            addSubtitle(createSubtitle(msg, type, true));
            return true;
        }
        return false;
    }

    // from TabbedChatDisplay
    public function tabClosed (localtype :String) :void
    {
        _closedTabs.add(localtype);
    }

    public function displayChat (display :Boolean) :void
    {
        if (display && !_target.containsOverlay(_historyOverlay)) {
            _target.addOverlay(_historyOverlay, PlaceBox.LAYER_CHAT_HISTORY);
        } else if (!display && _target.containsOverlay(_historyOverlay)) {
            _target.removeOverlay(_historyOverlay);
        }
    }

    /**
     * @return true if there are clickable glyphs under the specified point.
     */
    public function hasClickableGlyphsAtPoint (stageX :Number, stageY :Number) :Boolean
    {
        var stagePoint :Point = new Point(stageX, stageY);
        for each (var overlay :Sprite in getOverlays()) {
            if (overlay == null) {
                continue;
            }

            // NOTE: The docs swear up and down that the point needs to be in stage coords,
            // but only local coords seem to work. Bug?
            var objs :Array = overlay.getObjectsUnderPoint(overlay.globalToLocal(stagePoint));
            for each (var obj :DisplayObject in objs) {
                // the obj returned when hovering over text is the TextField, not the Chat Glyph
                if (obj.parent is ChatGlyph) {
                    if (_glyphsClickableAlways) {
                        (obj.parent as ChatGlyph).setClickable(true);
                        return true;
                    } else if ((obj.parent as ChatGlyph).isClickableAtPoint(stagePoint)) {
                        return true;
                    }
                } else if (obj is InteractiveObject && InteractiveObject(obj).mouseEnabled) {
                    return true;
                }
            }
        }
        return false;
    }

    public function setLocalType (localtype :String) :void
    {
        if (_localtype == localtype) {
            return;
        }

        if (_localtype != null) {
            // note when we stopped looking at the given localtype
            _localtypeDisplayTimes.put(_localtype, getTimer());
        }

        if (_closedTabs.contains(localtype)) {
            _closedTabs.remove(localtype);
        }

        _localtype = localtype;
        // TODO: display refreshery
    }

    /**
     * Set the target bounds to the given rectangle.  If targetBounds is null, this ChatOverlay will
     * use the default bounds.
     */
    public function setTargetBounds (targetBounds :Rectangle) :void
    {
        _targetBounds = targetBounds;
        layout();
    }

    /**
     * Sets whether or not the glyphs are clickable.
     */
    public function setClickableGlyphs (clickable :Boolean) :void
    {
        _glyphsClickableAlways = clickable;
    }

    /**
     * Remove a glyph from the overlay.
     */
    public function removeGlyph (glyph :ChatGlyph) :void
    {
        if (glyph.parent != null) {
            glyph.parent.removeChild(glyph);
        }
        glyph.wasRemoved();
    }

    /**
     * Callback from a ChatGlyph when it wants to be removed.
     */
    public function glyphExpired (glyph :ChatGlyph) :void
    {
        ArrayUtil.removeFirst(_subtitles, glyph);
        if (getOverlays().indexOf(glyph.parent) != -1) {
            removeGlyph(glyph);
        }
    }

    public function getTargetTextWidth () :int
    {
        var w :int = _targetBounds.width - ScrollBar.THICKNESS;
        // there is PAD between the text and the edges of the bubble, and another PAD between the
        // bubble and the container edges, on each side for a total of 4 pads.
        w -= (PAD * 4);
        return w;
    }

    /**
     * Used by ChatGlyphs to draw the shape on their Graphics.
     */
    public function drawSubtitleShape (g :Graphics, type :int, width :int, height :int) :int
    {
        var outline :uint = getOutlineColor(type);
        var background :uint;
        if (BLACK == outline) {
            background = WHITE;
        } else {
            background = ColorUtil.blend(WHITE, outline, .8);
        }
        width += PAD * 2;

        var shapeFunction :Function = getSubtitleShape(type);

        // clear any old graphics
        g.clear();

        // fill and outline in the same step
        g.lineStyle(1, outline);
        g.beginFill(background);
        shapeFunction(g, width, height);
        g.endFill();

        return PAD;
    }

    protected function localtypeExpired (localtype :String) :void
    {
        _localtypeDisplayTimes.remove(localtype);
    }

    protected function getOverlays () :Array
    {
        return [ _historyOverlay, _occupantList ];
    }

    protected function handlePrefsUpdated (event :ConfigValueSetEvent) :void
    {
        switch (event.name) {
        case Prefs.CHAT_HISTORY:
            setHistoryEnabled(Boolean(event.value) || Prefs.getSlidingChatHistory());
            break;

        case Prefs.CHAT_SLIDING:
            setHistorySliding(Boolean(event.value));
            break;

        case Prefs.OCCUPANT_LIST:
            setOccupantListShowing(Boolean(event.value));
            break;

        case Prefs.CHAT_FONT_SIZE:
            createStandardFormats();
            layout();
            break;
        }
    }

    protected function setHistoryEnabled (historyEnabled :Boolean) :void
    {
        // TODO
    }

    protected function setHistorySliding (sliding :Boolean) :void
    {
        // TODO
    }

    protected function setOccupantListShowing (showing :Boolean) :void
    {
        // TODO
    }

    protected function isHistoryMode () :Boolean
    {
        return (_historyBar != null);
    }

    protected function getDefaultTargetBounds () :Rectangle
    {
        return new Rectangle(0, 0, DEFAULT_WIDTH + ScrollBar.THICKNESS, DEFAULT_HEIGHT);
    }

    protected function layout () :void
    {
        if (_targetBounds == null) {
            _targetBounds = getDefaultTargetBounds();
        } 
    }

    /**
     * Add the specified subtitle glyph for immediate display.
     */
    protected function addSubtitle (glyph :SubtitleGlyph) :void
    {
        var height :int = int(glyph.height);
        glyph.x = _targetBounds.x + PAD;
        glyph.y = _targetBounds.bottom - height - PAD;
        scrollUpSubtitles(height + 1);
        _subtitles.push(glyph);
        _historyOverlay.addChild(glyph);
    }

    /**
     * Create a subtitle glyph.
     */
    protected function createSubtitle (msg :ChatMessage, type :int, expires :Boolean) :SubtitleGlyph
    {
        var texts :Array = formatMessage(msg, type, true, _userSpeakFmt);
        var lifetime :int = getLifetime(msg, expires);
        return new SubtitleGlyph(this, type, lifetime, _defaultFmt, texts);
    }

    /**
     * Get the subtitle for the specified history index, creating if necessary.
     */
    protected function getHistorySubtitle (index :int) :SubtitleGlyph
    {
        var glyph :SubtitleGlyph;

        // do a brute search (over a small set) for an already-created glyph
        for each (glyph in _showingHistory) {
            if (glyph.histIndex == index) {
                return glyph;
            }
        }

        // it looks like we've got to create a new one
        var msg :ChatMessage = _ctx.getMsoyChatDirector().getHistoryList().get(index).msg;
        glyph = createSubtitle(msg, getType(msg, true), false);
        glyph.histIndex = index;
        _showingHistory.push(glyph);
        return glyph;
    }

    /**
     * Return an array of Strings and TextFormats for creating a ChatGlyph.
     */
    protected function formatMessage (
        msg :ChatMessage, type :int, forceSpeaker :Boolean, userSpeakFmt :TextFormat) :Array
    {
        // first parse the message text into plain and links
        var texts :Array = parseLinks(msg.message, userSpeakFmt, shouldParseSpecialLinks(type));

        // possibly insert the formatting
        if (forceSpeaker || alwaysUseSpeaker(type)) {
            var format :String = msg.getFormat();
            if (format != null) {
                var umsg :UserMessage = (msg as UserMessage);
                var prefix :String = _msgMan.getBundle(MsoyCodes.CHAT_MSGS).get(
                    format, umsg.getSpeakerDisplayName()) + " ";

                if (useQuotes(msg, type)) {
                    prefix += "\"";
                    texts.push("\"");
                }
                texts.unshift(prefix);
            }
        }

        return texts;
    }

    /**
     * Return an array of text strings, with any string needing special formatting preceeded by
     * that format.
     */
    protected function parseLinks (
        text :String, userSpeakFmt :TextFormat, parseSpecial :Boolean) :Array
    {
        // parse the text into an array with urls at odd elements
        var array :Array = StringUtil.parseURLs(text);

        // insert the appropriate format before each element
        for (var ii :int = array.length - 1; ii >= 0; ii--) {
            if (ii % 2 == 0) {
                // normal text at even-numbered elements...
                if (parseSpecial) {
                    var specialBits :Array = parseSpecialLinks(String(array[ii]), userSpeakFmt);
                    specialBits.unshift(ii, 1);
                    array.splice.apply(array, specialBits);

                } else {
                    // just insert the speak format before the text
                    array.splice(ii, 0, userSpeakFmt);
                }

            } else {
                // links at the odd indexes
                array.splice(ii, 0, createLinkFormat(String(array[ii]), userSpeakFmt));
            }
        }

        return array;
    }

    /**
     * Parse any "special links" (in the format "[text|url]") in the specified text.
     *
     * @return an array containing [ format, text, format, text, ... ].
     */
    protected function parseSpecialLinks (text :String, userSpeakFmt :TextFormat) :Array
    {
        var array :Array = [];

        var result :Object;
        do {
            result = _specialLinkRegExp.exec(text);
            if (result != null) {
                var index :int = int(result.index);
                array.push(userSpeakFmt, text.substring(0, index));
                array.push(createLinkFormat(String(result[2]), userSpeakFmt), String(result[1]));

                // and advance the text
                var match :String = String(result[0]);
                text = text.substring(index + match.length);

            } else {
                // it's just left-over text
                array.push(userSpeakFmt, text);
            }

        } while (result != null);

        return array;
    }

    /**
     * (Re)create the standard formats.
     */
    protected function createStandardFormats () :void
    {
        // NOTE: Any null values in the override formats will use the value from the default, so if
        // a property is added to the default then it should be explicitely negated if not desired
        // in an override.
        _defaultFmt = new TextFormat();
        _defaultFmt.font = FONT;
        _defaultFmt.size = Prefs.getChatFontSize();
        _defaultFmt.color = 0x000070;
        _defaultFmt.bold = false;
        _defaultFmt.underline = false;

        _userSpeakFmt = createChatFormat();
    }

    /**
     * Create a link format for the specified link text.
     */
    protected function createLinkFormat (url :String, userSpeakFmt :TextFormat) :TextFormat
    {
        var fmt :TextFormat = new TextFormat();
        fmt.align = userSpeakFmt.align;
        fmt.font = FONT;
        fmt.size = Prefs.getChatFontSize();
        fmt.underline = true;
        fmt.color = 0x0093dd;
        fmt.bold = true;
        fmt.url = "event:" + url;
        return fmt;
    }

    /**
     * Get the lifetime, in milliseconds, of the specified chat message.
     */
    protected function getLifetime (msg :ChatMessage, expires :Boolean) :int
    {
        if (expires) {
            return getChatExpire(msg.timestamp, msg.message) - msg.timestamp;
        }
        return int.MAX_VALUE;
    }

    /**
     * Get the expire time for the specified chat.
     */
    protected function getChatExpire (stamp :int, text :String) :int
    {
        // load the configured durations
        var durations :Array =
            (DISPLAY_DURATION_PARAMS[getDisplayDurationIndex()] as Array);

        // start the computation from the maximum of the timestamp
        // or our last expire time.
        var start :int = Math.max(stamp, _lastExpire);

        // set the next expire to a time proportional to the text length.
        _lastExpire = start + Math.min(text.length * int(durations[0]),
                                       int(durations[2]));

        // but don't let it be longer than the maximum display time.
        _lastExpire = Math.min(stamp + int(durations[2]), _lastExpire);

        // and be sure to pop up the returned time so that it is above the min.
        return Math.max(stamp + int(durations[1]), _lastExpire);
    }

    /**
     * Should we be using quotes with the specified format?
     */
    protected function useQuotes (msg: ChatMessage, type :int) :Boolean
    {
        return (modeOf(type) != EMOTE) && !(msg is ChannelMessage);
    }

    /**
     * Should we force the use of the speaker in the formatting of
     * the message?
     */
    protected function alwaysUseSpeaker (type :int) :Boolean
    {
        return (modeOf(type) == EMOTE) || (placeOf(type) == BROADCAST);
    }

    /**
     * Should we parse "special links" (in the format "[text|url]") for chat messages
     * of the specified type.
     */
    protected function shouldParseSpecialLinks (type :int) :Boolean
    {
        switch (type) {
        case FEEDBACK:
        case INFO:
        case ATTENTION:
        case NOTIFICATION:
            return true;

        default:
            return false;
        }
    }

    /**
     * Get the outline color for the specified chat type.
     */
    protected function getOutlineColor (type :int) :uint
    {
        // mask out the bits we don't need for determining outline color
        switch (placeOf(type)) {
        case BROADCAST: return BROADCAST_COLOR;
        case TELL: return TELL_COLOR;
        case TELLFEEDBACK: return TELLFEEDBACK_COLOR;
        case INFO: return INFO_COLOR;
        case FEEDBACK: return FEEDBACK_COLOR;
        case ATTENTION: return ATTENTION_COLOR;
        case NOTIFICATION: return NOTIFY_COLOR;
        case CHANNEL: return CHANNEL_COLOR;
        default:
            switch (placeOf(type)) {
            case GAME: return GAME_COLOR;
            default: return BLACK;
            }
        }
    }

    /**
     * Get the function that draws the subtitle shape for the
     * specified type of subtitle.
     */
    protected function getSubtitleShape (type :int) :Function
    {
        switch (placeOf(type)) {
        case PLACE: {
            switch (modeOf(type)) {
            case SPEAK:
            default:
                return drawRoundedSubtitle;

            case EMOTE:
                return drawEmoteSubtitle;

            case THINK:
                return drawThinkSubtitle;
            }
        }

        case FEEDBACK:
            return drawFeedbackSubtitle;

        case BROADCAST:
        case CONTINUATION:
        case INFO:
        case ATTENTION:
        default:
            return drawRectangle;
        }
    }

    /** Subtitle draw function. See getSubtitleShape() */
    protected function drawRectangle (g :Graphics, w :int, h :int) :void
    {
        g.drawRect(0, 0, w, h);
    }

    /** Subtitle draw function. See getSubtitleShape() */
    protected function drawRoundedSubtitle (g :Graphics, w :int, h :int) :void
    {
        g.drawRoundRect(0, 0, w, h, PAD * 2, PAD * 2);
    }

    /** Subtitle draw function. See getSubtitleShape() */
    protected function drawEmoteSubtitle (g :Graphics, w :int, h :int) :void
    {
        g.moveTo(0, 0);
        g.lineTo(w, 0);
        g.curveTo(w - PAD, h / 2, w, h);
        g.lineTo(0, h);
        g.curveTo(PAD, h / 2, 0, 0);
    }

    /** Subtitle draw function. See getSubtitleShape() */
    protected function drawThinkSubtitle (g :Graphics, w :int, h :int) :void
    {
        // thinky bubbles on the left and right
        const DIA :int = 8;
        g.moveTo(PAD/2, 0);
        g.lineTo(w - PAD/2, 0);

        var yy :int;
        var ty :int;
        for (yy = 0; yy < h; yy += DIA) {
            ty = Math.min(h, yy + DIA);
            g.curveTo(w, (yy + ty)/2, w - PAD/2, ty);
        }

        g.lineTo(PAD/2, h);
        for (yy = h; yy > 0; yy -= DIA) {
            ty = Math.max(0, yy - DIA);
            g.curveTo(0, (yy + ty)/2, PAD/2, ty);
        }
    }

    /** Subtitle draw function. See getSubtitleShape() */
    protected function drawFeedbackSubtitle (g :Graphics, w :int, h :int) :void
    {
        g.moveTo(PAD / 2, 0);
        g.lineTo(w, 0);
        g.lineTo(w - PAD / 2, h);
        g.lineTo(0, h);
        g.lineTo(PAD / 2, 0);
    }

    /**
     * Convert the message class/localtype/mode into our internal type code.
     */
    protected function getType (msg :ChatMessage, history :Boolean) :int
    {
        var localtype :String = msg.localtype;

        if (msg is TellFeedbackMessage) {
            return (msg as TellFeedbackMessage).isFailure() ? FEEDBACK : TELLFEEDBACK;

        } else if (msg is UserMessage) {
            var type :int;
            var channelType :int = ChatChannel.typeOf(localtype);
            // TODO: jabber messages should probably have their own format
            if (channelType == ChatChannel.MEMBER_CHANNEL || 
                channelType == ChatChannel.JABBER_CHANNEL) {
                type = TELL;
            } else if (msg is ChannelMessage && 
                ChatChannel.typeOf(localtype) != ChatChannel.ROOM_CHANNEL) {
                type = CHANNEL;
            } else {
                type = PLACE;
            }
            // factor in the mode
            switch ((msg as UserMessage).mode) {
            case ChatCodes.DEFAULT_MODE:
                return type | SPEAK;
            case ChatCodes.EMOTE_MODE:
                return type | EMOTE;
            case ChatCodes.THINK_MODE:
                return type | THINK;
            case ChatCodes.SHOUT_MODE:
                return type | SHOUT;
            case ChatCodes.BROADCAST_MODE:
                return BROADCAST; // broadcast always looks like broadcast
            }

        } else if (msg is SystemMessage) {
            switch ((msg as SystemMessage).attentionLevel) {
            case SystemMessage.INFO:
                return INFO;
            case SystemMessage.FEEDBACK:
                return FEEDBACK;
            case SystemMessage.ATTENTION:
                return ATTENTION;
            default:
                log.warning("Unknown attention level for system message " + "[msg=" + msg + "].");;
                break;
            }

            // otherwise
            return IGNORECHAT;

        } else if (msg is NotifyMessage) {
            return NOTIFICATION;
        }

        log.warning("Skipping received message of unknown type [msg=" + msg + "].");
        return IGNORECHAT;
    }

    /**
     * Scroll up all the subtitles by the specified amount.
     */
    protected function scrollUpSubtitles (dy :int) :void
    {
        for (var ii :int = 0; ii < _subtitles.length; ii++) {
            var glyph :ChatGlyph = (_subtitles[ii] as ChatGlyph);
            var newY :int = int(glyph.y) - dy;
            if (newY <= getMinHistY()) {
                _subtitles.splice(ii, 1);
                ii--;
                removeGlyph(glyph);

            } else {
                glyph.y = newY;
            }
        }
    }

    /**
     * Extract the mode constant from the type value.
     */
    protected function modeOf (type :int) :int
    {
        return (type & 0xF);
    }

    /**
     * Extract the place constant from the type value. 
     */
    protected function placeOf (type :int) :int
    {
        return (type & ~0xF);
    }

    /**
     * Get the display duration parameters.
     */
    protected function getDisplayDurationIndex () :int
    {
        // by default we add one, because it's assumed that we're in
        // subtitle-only view.
        return Prefs.getChatDecay() + 1;
    }

    /**
     * Remove all the glyphs in the specified list.
     */
    protected function clearGlyphs (glyphs :Array) :void
    {
        for each (var glyph :ChatGlyph in glyphs) {
            removeGlyph(glyph);
        }

        glyphs.length = 0; // array truncation
    }

    protected function getMinHistY () :int
    {
        return _targetBounds.y +
            ((_occupantList != null && _includeOccList && Prefs.getShowingOccupantList()) ? 
              _occupantList.y + _occupantList.height : 0);
    }

    private static const log :Log = Log.getLog(ChatOverlay);

    /** Used to guess at the 'page size' for the scrollbar. */
    protected static const SUBTITLE_HEIGHT_GUESS :int = 26;

    /**
     * Times to display chat.
     * { (time per character), (min time), (max time) }
     *
     * Groups 0/1/2 are short/medium/long for chat bubbles,
     * and groups 1/2/3 are short/medium/long for subtitles.
     */
    protected static const DISPLAY_DURATION_PARAMS :Array = [
        [ 125, 10000, 30000 ],
        [ 200, 15000, 40000 ],
        [ 275, 20000, 50000 ],
        [ 350, 25000, 60000 ]
    ];

    /** Type mode code for default chat type (speaking). */
    protected static const SPEAK :int = 0;

    /** Type mode code for shout chat type. */
    protected static const SHOUT :int = 1;

    /** Type mode code for emote chat type. */
    protected static const EMOTE :int = 2;

    /** Type mode code for think chat type. */
    protected static const THINK :int = 3;

    /** Type place code for default place chat (cluster, scene). */
    protected static const PLACE :int = 1 << 4;

    /** Our internal code for tell chat. */
    protected static const TELL :int = 2 << 4;

    /** Our internal code for tell feedback chat. */
    protected static const TELLFEEDBACK :int = 3 << 4;
    
    /** Our internal code for info system messges. */
    protected static const INFO :int = 4 << 4;
    
    /** Our internal code for feedback system messages. */
    protected static const FEEDBACK :int = 5 << 4;

    /** Our internal code for attention system messages. */
    protected static const ATTENTION :int = 6 << 4;

    /** Type place code for broadcast chat type. */
    protected static const BROADCAST :int = 7 << 4;

    /** Type code for a chat type that was used in some special context,
     * like in a negotiation. */
    protected static const SPECIALIZED :int = 8 << 4;

    /** Our internal code for any type of chat that is continued in a
     * subtitle. */
    protected static const CONTINUATION :int = 9 << 4;

    /** Type code for game chat. */
    protected static const GAME :int = 10 << 4;

    /** Type code for notifications. */
    protected static const NOTIFICATION :int = 11 << 4;

    /** Our internal code for channel chat. This is currently unused, as all channel chat is 
     * associated with a place.  If we have private, non-place channels in the future, this will
     * be used again. */
    protected static const CHANNEL :int = 12 << 4;

    /** Our internal code for a chat type we will ignore. */
    protected static const IGNORECHAT :int = -1;

    /** The default width to use for the chat history */
    protected static const DEFAULT_WIDTH :int = 300;

    /** The default height to use for the chat history */
    protected static const DEFAULT_HEIGHT :int = 500;

    // used to color chat bubbles
    protected static const BROADCAST_COLOR :uint = 0x990000;
    protected static const FEEDBACK_COLOR :uint = 0x00AA00;
    protected static const TELL_COLOR :uint = 0x0000AA;
    protected static const TELLFEEDBACK_COLOR :uint = 0x00AAAA;
    protected static const INFO_COLOR :uint = 0xAAAA00;
    protected static const ATTENTION_COLOR :uint = 0xFF5000;
    protected static const GAME_COLOR :uint = 0x777777;
    protected static const NOTIFY_COLOR :uint = 0x008A83;
    protected static const CHANNEL_COLOR :uint = 0x5500AA;
    protected static const BLACK :uint = 0x000000;
    protected static const WHITE :uint = 0xFFFFFF;

    /** The font for all chat. */
    protected static const FONT :String = "Arial";

    /** Expire localtype display time rememberings after a minute. */
    protected static const LOCALTYPE_EXPIRE_TIME :Number = 60;

    protected var _ctx :MsoyContext;
    protected var _chatContainer :ChatContainer;
    protected var _includeOccList :Boolean;
    protected var _localtype :String;
    protected var _localtypeDisplayTimes :HashMap = new HashMap();
    protected var _closedTabs :ExpiringSet;

    /** Used to translate messages. */
    protected var _msgMan :MessageManager;

    /** The overlay we place on top of our target that contains all the history subtitle chat
     * glyphs. */
    protected var _historyOverlay :Sprite = new Sprite();

    /** The list that contains names and headshots of everyone current subscribed to the currently
     * shown channel */
    protected var _occupantList :ChannelOccupantList;

    /** The target container over which we're overlaying chat. */
    protected var _target :LayeredContainer;

    /** The region of our target over which we render. */
    protected var _targetBounds :Rectangle;

    /** The currently displayed list of subtitles. */
    protected var _subtitles :Array = [];

    /** The currently displayed subtitles in history mode. */
    protected var _showingHistory :Array = [];

    /** The history offset (from 0) such that the history lines (0, _histOffset - 1) will all fit
     * onscreen if the lowest scrollbar positon is _histOffset. */
    protected var _histOffset :int = 0;

    /** True if the histOffset does need to be recalculated. */
    protected var _histOffsetFinal :Boolean = false;

    /** A guess of how many history lines fit onscreen at a time. */
    protected var _historyExtent :int;

    /** The unbounded expire time of the last chat glyph displayed. */
    protected var _lastExpire :int;

    /** The default text format to be applied to subtitles. */
    protected var _defaultFmt :TextFormat;

    /** The format for user-entered text. */
    protected var _userSpeakFmt :TextFormat;

    /** Matches "special links", which are in the format "[text|url]". */
    protected var _specialLinkRegExp :RegExp = new RegExp("\\[(.+?)\\|(.+?)\\]");

    /** The history scrollbar. */
    protected var _historyBar :VScrollBar;

    /** True while we're setting the position on the scrollbar, so that we
     * know to ignore the event. */
    protected var _settingBar :Boolean = false;

    /** The side to keep the scroll bar for this overlay on. */
    protected var _scrollBarSide :int;

    /** Whether we should always allow the chat glyphs to capture the mouse (for text selection) */
    protected var _glyphsClickableAlways :Boolean = false;

}
}

import flash.display.Sprite;

import mx.controls.scrollClasses.ScrollBar;

import mx.core.Container;
import mx.core.ScrollPolicy;
import mx.core.UIComponent;

import com.threerings.flex.FlexWrapper;

import com.threerings.util.Log;

import com.threerings.msoy.client.PlaceLayer;

import com.threerings.msoy.chat.client.ChannelOccupantList;
import com.threerings.msoy.chat.client.ChatOverlay;

class ChatContainer extends Container
{
    public function ChatContainer (scrollBar :ScrollBar, chat :Sprite) 
    {
        styleName = "chatContainer";
        autoLayout = false;
        horizontalScrollPolicy = ScrollPolicy.OFF
        addChild(scrollBar);
        addChild(new FlexWrapper(chat));
    }

    public function addOccupantList (occList :ChannelOccupantList) :void
    {
        if (_occList != null && _occList != occList) {
            removeChild(_occList);
        }

        addChild(_occList = occList);
    }

    public function clearOccupantList () :void
    {
        if (_occList != null) {
            removeChild(_occList);
            _occList = null;
        }
    }

    private static const log :Log = Log.getLog(ChatContainer);

    protected var _occList :ChannelOccupantList;
}
