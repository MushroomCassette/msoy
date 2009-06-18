//
// $Id$

package client.util;

import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.Widget;

import client.shell.CShell;
import client.ui.MsoyUI;
import client.ui.PromptPopup;

/**
 * Allows one to wire up a button and a service call into one concisely specified little chunk of
 * code. Be sure to call <code>super.onSuccess()</code> and <code>super.onFailure()</code> if you
 * override those methods so that they can automatically reenable the trigger button.
 */
public abstract class ClickCallback<T> extends com.threerings.gwt.util.ClickCallback<T>
{
    /**
     * Creates a callback for the supplied trigger (the constructor will automatically add this
     * callback to the trigger as a click listener). Failure will automatically be reported.
     */
    public ClickCallback (HasClickHandlers trigger)
    {
        this(trigger, null);
    }

    /**
     * Creates a callback for the supplied trigger (the constructor will automatically add this
     * callback to the trigger as a click listener). Failure will automatically be reported.
     *
     * @param confirmMessage if non-null, a confirm dialog will be popped up when the button is
     * clicked and the service call wil only be made if the user confirms the dialog.
     */
    public ClickCallback (HasClickHandlers trigger, String confirmMessage)
    {
        super(trigger);
        _confirmMessage = confirmMessage;
    }

    @Override // from ClickCallback
    protected void reportFailure (Throwable cause)
    {
        Widget errorNear = getErrorNear();
        if (errorNear != null) {
            if (errorNear instanceof FocusWidget) {
                ((FocusWidget)errorNear).setFocus(true);
            }
            MsoyUI.errorNear(convertError(cause), errorNear);
        } else {
            MsoyUI.error(convertError(cause));
        }
    }

    /**
     * Returns additional information to be added to the confirmation prompt, if we have one. By
     * default we don't.
     */
    protected String getPromptContext ()
    {
        return null;
    }

    /**
     * Converts an exception returned by the server into a readable message.
     */
    protected String convertError (Throwable cause)
    {
        return CShell.serverError(cause);
    }

    @Override // from ClickCallback
    protected void takeAction ()
    {
        takeAction(false); // route action through our confirmation process
    }

    protected void takeAction (boolean confirmed)
    {
        // if we have not yet confirmed and desire to do so, show the confirm popup
        if (getConfirmMessage() != null && !confirmed) {
            setEnabled(false);
            displayPopup();
            return;
        }

        // we're confirmed or don't want to, so go ahead and call the service
        super.takeAction();
    }

    protected void displayPopup ()
    {
        new PromptPopup(getConfirmMessage(), null) {
            public void onAffirmative () {
                setEnabled(true);
                takeAction(true);
            }
            public void onNegative () {
                setEnabled(true);
            }
        }.setContext(getPromptContext()).prompt();
    }

    protected String getConfirmMessage ()
    {
        return _confirmMessage;
    }

    protected Widget getErrorNear ()
    {
        return null;
    }

    protected String _confirmMessage;
}
