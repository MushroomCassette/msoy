//
// $Id$

package client.me;

import com.google.gwt.core.client.GWT;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.FloatPanel;
import com.threerings.gwt.ui.WidgetUtil;

import com.threerings.msoy.person.gwt.MeService;
import com.threerings.msoy.person.gwt.MeServiceAsync;
import com.threerings.msoy.person.gwt.MyWhirledData;
import com.threerings.msoy.web.gwt.Pages;

import client.person.FriendsFeedPanel;
import client.person.PersonMessages;
import client.shell.CShell;
import client.ui.MsoyUI;
import client.ui.RoundBox;
import client.util.Link;
import client.util.MsoyCallback;
import client.util.ServiceUtil;

public class MyWhirled extends FlowPanel
{
    public MyWhirled ()
    {
        setStyleName("myWhirled");

        _mesvc.getMyWhirled(new MsoyCallback<MyWhirledData>() {
            public void onSuccess (MyWhirledData data) {
                init(data);
            }
        });
    }

    protected void init (MyWhirledData data)
    {
        RoundBox rbits = new RoundBox(RoundBox.MEDIUM_BLUE);
        rbits.addStyleName("QuickNav");
        rbits.add(MsoyUI.createLabel(_msgs.populationDisplay(""+data.whirledPopulation), null));
        rbits.add(makeQuickLink("My Profile", Pages.PEOPLE, ""+CShell.getMemberId()));
        rbits.add(makeQuickLink("My Passport", Pages.ME, "passport"));

        String empty = data.friendCount > 0 ? _pmsgs.emptyFeed() : _pmsgs.emptyFeedNoFriends();
        FriendsFeedPanel feed = new FriendsFeedPanel(empty, data.feed);
        FlowPanel feedBox = MsoyUI.createFlowPanel("FeedBox");
        feedBox.add(new Image("/images/me/me_feed_topcorners.png"));
        feedBox.add(MsoyUI.createLabel(_msgs.newsTitle(), "NewsTitle"));
        feedBox.add(feed);
        feedBox.add(new Image("/images/me/me_feed_bottomcorners.png"));

        // news feed on the left, bits and friends on the right
        FloatPanel horiz = new FloatPanel("NewsAndFriends");
        horiz.add(feedBox);
        FlowPanel right = MsoyUI.createFlowPanel("RightBits");
        right.add(rbits);
        right.add(WidgetUtil.makeShim(10, 10));
        right.add(new MeFriendsPanel(data));
        horiz.add(right);
        add(horiz);
    }

    protected Widget makeQuickLink (String label, Pages page, String args)
    {
        // TODO: add a little bullet to the left
        Widget link = Link.create(label, page, args);
        link.removeStyleName("inline");
        return link;
    }

    protected static final MeMessages _msgs = (MeMessages)GWT.create(MeMessages.class);
    protected static final PersonMessages _pmsgs = (PersonMessages)GWT.create(PersonMessages.class);
    protected static final MeServiceAsync _mesvc = (MeServiceAsync)
        ServiceUtil.bind(GWT.create(MeService.class), MeService.ENTRY_POINT);
}
