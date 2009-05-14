//
// $Id$

package client.games;

import java.util.List;

import com.google.gwt.core.client.GWT;

import com.threerings.msoy.game.gwt.GameInfo;
import com.threerings.msoy.game.gwt.GameService;
import com.threerings.msoy.game.gwt.GameServiceAsync;
import com.threerings.msoy.web.gwt.Args;
import com.threerings.msoy.web.gwt.Pages;

import client.util.InfoCallback;
import client.util.Link;
import client.util.ServiceUtil;

/**
 * Displays games in a particular genre, or of all genres for the "All Games" page.
 * For genre pages it displays a featured game; for "All Games" page displays a search.
 */
public class GameGenrePanel extends GameListPanel
{
    public GameGenrePanel (byte genre, byte sortMethod, String query)
    {
        super(genre, sortMethod);

        _header.setQuery(query);
        _gamesvc.loadGameGenre(genre, sortMethod, query, new InfoCallback<List<GameInfo>>() {
            public void onSuccess (List<GameInfo> games) {
                init(games);
            }
        });
    }

    protected void onSortChanged (byte sortMethod)
    {
        Link.go(Pages.GAMES, Args.compose("g", _genre, sortMethod, _header.getQuery()));
    }

    protected static final GameServiceAsync _gamesvc = (GameServiceAsync)
        ServiceUtil.bind(GWT.create(GameService.class), GameService.ENTRY_POINT);
}
