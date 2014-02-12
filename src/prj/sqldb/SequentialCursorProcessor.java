package prj.sqldb;

import android.database.Cursor;

import java.util.Iterator;
import java.util.List;

public class SequentialCursorProcessor implements Iterator<QueryResult>
{
    private final Iterator<QueryParams> _queryParams;
    private final SqlDb.IQueryProcessor _queryProcessor;
    private Cursor _currentCursor;

    public SequentialCursorProcessor(SqlDb.IQueryProcessor processor,
                                     List<QueryParams> params)
    {
        _queryParams = params.iterator();
        _queryProcessor = processor;
    }

    @Override
    public boolean hasNext()
    {
        return _queryParams.hasNext();
    }

    @Override
    public QueryResult next()
    {
        closeCursor();
        QueryParams p = _queryParams.next();
        _currentCursor = _queryProcessor.process(p);
        return new QueryResult(p, _currentCursor);
    }

    @Override
    public void remove()
    {
        closeCursor();
        _queryParams.remove();
    }

    private void closeCursor()
    {
        if (_currentCursor != null && !_currentCursor.isClosed())
        {
            _currentCursor.close();
        }
    }
}
