//
// $Id$

package com.threerings.msoy.item.web;

/**
 * A digital item representing a simple text document.
 */
public class Document extends Item
{
    /** The document media. */
    public MediaDesc docMedia;

    /** The title of this document (max length 255 characters). */
    public String title;

    // @Override from Item
    public byte getType ()
    {
        return DOCUMENT;
    }

    // @Override from Item
    public String getDescription ()
    {
        return title;
    }

    // @Override
    public boolean isConsistent ()
    {
        return super.isConsistent() && (docMedia != null) && nonBlank(title);
    }
}
