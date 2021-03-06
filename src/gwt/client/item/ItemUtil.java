//
// $Id$

package client.item;

import com.google.common.base.Strings;
import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.HTML;

import com.threerings.orth.data.MediaDesc;

import com.threerings.gwt.ui.WidgetUtil;

import com.threerings.msoy.data.all.DeploymentConfig;
import com.threerings.msoy.data.all.UberClientModes;
import com.threerings.msoy.item.data.all.Avatar;
import com.threerings.msoy.item.data.all.Decor;
import com.threerings.msoy.item.data.all.Furniture;
import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.Launcher;
import com.threerings.msoy.item.data.all.Pet;
import com.threerings.msoy.item.data.all.Toy;

import client.shell.CShell;
import client.shell.ShellMessages;
import client.ui.MsoyUI;
import client.util.FlashClients;

/**
 * Contains utility methods for item related user interface business.
 */
public class ItemUtil
{
    /**
     * Returns the name of this item or a properly translated string indicating that it has no
     * name.
     */
    public static String getName (Item item)
    {
        return getName(item, false);
    }

    /**
     * Returns the truncated name of this item or a properly translated string indicating that it
     * has no name.
     */
    public static String getName (Item item, boolean truncate)
    {
        String name = Strings.isNullOrEmpty(item.name) ? _cmsgs.noName() : item.name;
        if (name.length() > 32 && truncate) {
            name = _cmsgs.truncName(name.substring(0, 29));
        }
        return name;
    }

    /**
     * Returns the description of this item or a properly translated string indicating that it has
     * no description.
     */
    public static String getDescription (Item item)
    {
        return (item.description.trim().length() == 0) ?
            _cmsgs.noDescrip() : item.description;
    }

    /**
     * Creates a generic item viewer, mostly using the UberClient!
     */
    public static HTML createViewer (
        Item item, boolean inShop, boolean userOwnsItem, String memories)
    {
        int w = 360;
        int h = 385;

        int mode;
        if (item instanceof Avatar) {
            mode = UberClientModes.AVATAR_VIEWER;
        } else if (item instanceof Pet) {
            mode = UberClientModes.PET_VIEWER;
        } else if (item instanceof Decor) {
            mode = UberClientModes.DECOR_VIEWER;
        } else if (item instanceof Furniture) {
            mode = UberClientModes.FURNI_VIEWER;
        } else if (item instanceof Toy) {
            mode = UberClientModes.TOY_VIEWER;
        } else if (item instanceof Launcher) {
            mode = UberClientModes.LAUNCHER_VIEWER;
        } else {
            w = 320;
            h = 240;
            mode = UberClientModes.GENERIC_VIEWER;
        }

        // see if we need to display an upgrade message
        String definition = CShell.frame.checkFlashVersion(w, h);
        if (definition != null) {
            return MsoyUI.createHTML(definition, null);
        }

        MediaDesc preview = item.getPreviewMedia();

        // A special case for video, for now...
        if (preview.isVideo()) {
            return FlashClients.createVideoPlayer(320, 240, preview.getMediaPath());
        } else if (preview.isAudio()) {
            return FlashClients.createAudioPlayer(320, 68, preview.getMediaPath());
        }

        // set up the flashvars
        String flashVars = "mode=" + mode +
            "&media=" + URL.encodeComponent(preview.getMediaPath()) +
            "&name=" + URL.encodeComponent(item.name) +
            "&env=" + (inShop ? "shop" : "inventory");
        if (memories != null) {
            flashVars += "&mems=" + URL.encodeComponent(memories);
        }
        switch (mode) {
        case UberClientModes.AVATAR_VIEWER:
            flashVars += "&scale=" + ((Avatar) item).scale;
            if (userOwnsItem) {
                flashVars += "&scaling=true";
            }
            break;

        case UberClientModes.FURNI_VIEWER:
            flashVars += "&hotSpotX=" + ((Furniture) item).hotSpotX +
                "&hotSpotY=" + ((Furniture) item).hotSpotY;
            break;

        case UberClientModes.DECOR_VIEWER:
            flashVars += "&" + createDecorViewerParams((Decor) item);
            break;
        }

        if (mode != UberClientModes.AVATAR_VIEWER) {
            flashVars += "&username=Tester";
        }

        // and emit the widget
        return WidgetUtil.createFlashContainer("viewer",
            "/clients/" + DeploymentConfig.version + "/world-client.swf", w, h, flashVars);
    }

    /**
     * Exposed. Also called from ItemRemixer.
     */
    public static String createDecorViewerParams (Decor decor)
    {
        return "decorType=" + decor.type + "&decorHideWalls=" + decor.hideWalls +
            "&decorWidth=" + decor.width + "&decorHeight=" + decor.height +
            "&decorDepth=" + decor.depth + "&decorHorizon=" + decor.horizon +
            "&decorActorScale=" + decor.actorScale + "&decorFurniScale=" + decor.furniScale +
            "&username=Test%20Avatar"; // add a name for the test avatar..
    }

    /**
     * Extract memories from the viewer. Used when purchasing.
     */
    public static String getMemories ()
    {
        return getMemoriesNative(findViewer());
    }

    public static void showViewerConfig ()
    {
        showViewerConfigNative(findViewer());
    }

    public static native Element findViewer () /*-{
        // do not go to "top"...
        return $wnd.document.getElementById("viewer");
    }-*/;

    /**
     * Does actual getMemories call.
     */
    protected static native String getMemoriesNative (Element viewer) /*-{
        if (viewer) {
            try { return viewer.getStudioMemories(); } catch (e) {} // block exceptions
        }
        return null;
    }-*/;

    /**
     * Does actual showViewerConfig call.
     */
    protected static native void showViewerConfigNative (Element viewer) /*-{
        if (viewer) {
            try { viewer.showStudioConfig(); } catch (e) {} // block exceptions
        }
    }-*/;

    protected static final ShellMessages _cmsgs = GWT.create(ShellMessages.class);
}
