package prj.sqldb;

import android.database.Cursor;

public class QueryResult
{
    private final QueryParams _q;
    private final Cursor _c;

    public QueryResult(QueryParams q, Cursor c)
    {
        _q = q;
        _c = c;
    }

    public QueryParams getQueryParams()
    {
        return _q;
    }

    public Cursor getCursor()
    {
        return _c;
    }
}
