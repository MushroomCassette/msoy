//
// $Id$

package com.threerings.util;

/**
 * An impostor for the real Name class that exists to allow MemberName to work in GWT.
 */
public class Name
    implements Comparable
{
    public Name ()
    {
    }

    public Name (String name)
    {
        _name = name;
    }

    public String getNormal ()
    {
        return normalize(_name);
    }

    public boolean isValid ()
    {
        return !isBlank();
    }

    public boolean isBlank ()
    {
        return (_name != null) && (_name.trim().length() > 0);
    }

    // @Override
    public String toString ()
    {
        return _name;
    }

    // @Override
    public int hashCode ()
    {
        return getNormal().hashCode();
    }

    // @Override
    public boolean equals (Object other)
    {
        if (other instanceof Name) {
            return getNormal().equals(((Name)other).getNormal());
        } else {
            return false;
        }
    }

    // from Comparable
    public int compareTo (Object o)
    {
        if (o instanceof Name) {
            return getNormal().compareTo(((Name)o).getNormal());
        } else {
            return toString().compareTo(o.toString());
        }
    }

    protected String normalize (String name)
    {
        return name.toLowerCase();
    }

    /** The raw name text. */
    protected String _name;
}
