package prj.sqldb;

public class QueryParams
{
    private final String _table;
    private final String[] _columns;
    private final String _selection;
    private final String[] _selectionArgs;
    private final String _groupBy;
    private final String _having;
    private final String _orderBy;
    private String _limit;

    public QueryParams(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy)
    {
        this._table = table;
        this._columns = columns;
        this._selection = selection;
        this._selectionArgs = selectionArgs;
        this._groupBy = groupBy;
        this._having = having;
        this._orderBy = orderBy;
    }

    public String getTable()
    {
        return _table;
    }

    public String[] getColumns()
    {
        return _columns;
    }

    public String getSelection()
    {
        return _selection;
    }

    public String[] getSelectionArgs()
    {
        return _selectionArgs;
    }

    public String getGroupBy()
    {
        return _groupBy;
    }

    public String getHaving()
    {
        return _having;
    }

    public String getOrderBy()
    {
        return _orderBy;
    }

    public String getLimit()
    {
        return _limit;
    }

    public void setLimit(String l)
    {
        _limit = l;
    }
}
