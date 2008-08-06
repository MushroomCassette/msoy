//
// $Id$

package client.me;

import java.util.List;

import com.google.gwt.core.client.GWT;

import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;

import com.threerings.msoy.badge.data.all.Badge;

import com.threerings.msoy.person.gwt.MeService;
import com.threerings.msoy.person.gwt.MeServiceAsync;

import client.ui.Marquee;
import client.ui.MsoyUI;
import client.util.MsoyCallback;
import client.util.ServiceUtil;

public class PassportPanel extends VerticalPanel
{
    public PassportPanel ()
    {
        setStyleName("passport");

        _mesvc.loadBadges(CMe.ident, new MsoyCallback<List<Badge>>() {
            public void onSuccess (List<Badge> badges) {
                init(badges);
            }
        });
    }

    protected void init (List<Badge> badges)
    {
        add(new NextPanel());
    }

    protected static class NextPanel extends VerticalPanel
    {
        public NextPanel ()
        {
            setStyleName("NextPanel");

            HorizontalPanel header = new HorizontalPanel();
            header.add(MsoyUI.createImage("/images/me/passport_icon.png", "Icon"));
            header.add(MsoyUI.createLabel(_msgs.passportDescription(), "Description"));
            header.add(new Marquee(null, _msgs.passportMarquee()));
            add(header);

            HorizontalPanel nextHeader = new HorizontalPanel();
            nextHeader.add(MsoyUI.createImage("/images/me/passport_header_left.png", null));
            HorizontalPanel headerContent = new HorizontalPanel();
            headerContent.setStyleName("NextHeaderContent");
            headerContent.setVerticalAlignment(HorizontalPanel.ALIGN_MIDDLE);
            headerContent.add(MsoyUI.createImage("/images/me/passport_next.png", null));
            Label label = MsoyUI.createLabel(_msgs.passportNextBar(), "NextHeaderText");
            headerContent.add(label);
            headerContent.setCellWidth(label, "100%");
            headerContent.add(MsoyUI.createImage("/images/me/passport_shuffle.png", null));
            nextHeader.add(headerContent);
            nextHeader.setCellWidth(headerContent, "100%");
            nextHeader.add(MsoyUI.createImage("/images/me/passport_header_right.png", null));
            add(nextHeader);
        }
    }

    protected static final MeMessages _msgs = GWT.create(MeMessages.class);
    protected static final MeServiceAsync _mesvc = (MeServiceAsync)
        ServiceUtil.bind(GWT.create(MeService.class), MeService.ENTRY_POINT);
}
