//
// $Id$

package client.inventory;

import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.WidgetUtil;

import com.threerings.msoy.item.web.Avatar;
import com.threerings.msoy.item.web.Furniture;
import com.threerings.msoy.item.web.Game;
import com.threerings.msoy.item.web.Item;
import com.threerings.msoy.item.web.MediaDesc;
import com.threerings.msoy.item.web.Photo;

import client.item.BaseItemDetailPopup;
import client.item.ItemContext;
import client.item.ItemDetailHost;
import client.shell.MsoyEntryPoint;
import client.util.ClickCallback;

/**
 * Displays a popup detail view of an item from the user's inventory.
 */
public class ItemDetailPopup extends BaseItemDetailPopup
{
    public ItemDetailPopup (ItemContext ctx, Item item, ItemDetailHost parent)
    {
        super(ctx, item);
        _host = parent;
    }

    // @Override // BaseItemDetailPopup
    protected Widget createPreview (Item item)
    {
        // TODO: maybe ItemUtil should handle this
        if (item instanceof Avatar) {
            MediaDesc avatarMedia = ((Avatar)_item).avatarMedia;
            String path = MsoyEntryPoint.toMediaPath(avatarMedia.getMediaPath());
            return WidgetUtil.createFlashContainer(
                "avatarViewer", "/clients/avatarviewer.swf", 300, 500,
                "avatar=" + URL.encodeComponent(path));
        } else {
            return super.createPreview(item);
        }
    }

    // @Override // BaseItemDetailPopup
    protected void createInterface (VerticalPanel details, VerticalPanel controls)
    {
        super.createInterface(details, controls);

        final InventoryContext ictx = (InventoryContext)_ctx;
        if (_item instanceof Furniture) {
            // TODO: "Action", ((Furniture)_item).action

        } else if (_item instanceof Game) {
            // TODO: ((Game)_item).name,
            // TODO: "# Players (Desired)", String.valueOf(((Game)_item).desiredPlayers));
            // TODO: "# Players (Minimum)", String.valueOf(((Game)_item).minPlayers),
            // TODO: "# Players (Maximum)", String.valueOf(((Game)_item).maxPlayers));

            int gameId = _item.getProgenitorId();
            controls.add(new HTML("<a href=\"/game/index.html#" + gameId + "\">" +
                                  ictx.imsgs.detailPlay() + "</a>"));

        } else if (_item instanceof Photo) {
            // TODO: "Caption", ((Photo)_item).caption);
        }

        // we'll need this now so that we can pass it to our click callbacks
        _status = new Label("");
        _status.setStyleName("itemDetailStatus");

        Button button;
        if (_item.parentId == 0) {
            button = new Button(ictx.imsgs.detailList());
            new ClickCallback(ictx, button, _status) {
                public boolean callService () {
                    // make sure the item is kosher; TODO: make this less of a hack
                    if (_item.name.trim().length() == 0) {
                        _status.setText(ictx.imsgs.errItemMissingName());
                        return false;
                    }
                    if (_item.description.trim().length() == 0) {
                        _status.setText(ictx.imsgs.errItemMissingDescrip());
                        return false;
                    }
                    ictx.catalogsvc.listItem(ictx.creds, _item.getIdent(), true, this);
                    return true;
                }
                public boolean gotResult (Object result) {
                    _status.setText(ictx.imsgs.msgItemListed());
                    return false; // don't reenable list button
                }
            };

        } else {
            button = new Button(ictx.imsgs.detailRemix());
            new ClickCallback(ictx, button, _status) {
                public boolean callService () {
                    ictx.itemsvc.remixItem(ictx.creds, _item.getIdent(), this);
                    return true;
                }
                public boolean gotResult (Object result) {
                    // TODO: update item panel
                    _status.setText(ictx.imsgs.msgItemRemixed());
                    return false; // don't reenable remix button
                }
            };
        }
        controls.add(button);

        button = new Button(ictx.imsgs.detailDelete());
        new ClickCallback(ictx, button, _status) {
            public boolean callService () {
                ictx.itemsvc.deleteItem(ictx.creds, _item.getIdent(), this);
                return true;
            }
            public boolean gotResult (Object result) {
                _host.itemDeleted(_item);
                hide();
                return false;
            }
        };
        controls.add(button);

        if (_item.parentId == 0) {
            button = new Button(ictx.imsgs.detailEdit());
            button.addClickListener(new ClickListener() {
                public void onClick (Widget sender) {
                    ItemEditor editor = _host.createItemEditor(_item.getType());
                    editor.setItem(_item);
                    editor.show();
                    hide();
                }
            });
            controls.add(button);
        }

        controls.add(_status);
    }

    protected ItemDetailHost _host;
    protected Label _status;
}
