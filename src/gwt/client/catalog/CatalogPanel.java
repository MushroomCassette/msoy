//
// $Id$

package client.catalog;

import com.google.gwt.user.client.ui.TabPanel;

import com.threerings.msoy.web.client.WebContext;

import com.threerings.msoy.item.web.Item;

/**
 * Displays a tabbed panel containing the catalog.
 */
public class CatalogPanel extends TabPanel
{
    public CatalogPanel (WebContext ctx)
    {
        setStyleName("catalog");
        // create item panels for our known item types
        add(new ItemPanel(ctx, Item.PHOTO), "Photos");
        add(new ItemPanel(ctx, Item.DOCUMENT), "Documents");
        add(new ItemPanel(ctx, Item.FURNITURE), "Furniture");
        add(new ItemPanel(ctx, Item.GAME), "Games");
        add(new ItemPanel(ctx, Item.AVATAR), "Avatars");
        selectTab(0);
    }
}
