package prj.sqldb;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

import prj.sqldb.threading.Later;
import prj.sqldb.threading.SqlDBThreads;


/**
 * Sqldb provides an async api to execute queries on the android SQLite
 * database.
 * Uses one thread for writing to DB as sqlite only supports one writer.
 * Uses one thread for reading from DB. This is sufficient for most usecases
 * by a margin.
 * <p/>
 * All methods return a future instead of 'void' in order to allow the
 * application thread to wait till
 * there is a result, if the application so desires.
 * <p/>
 * TODO: offer support for multiple readers from db.
 */
public class SqlDb
{
    private final SQLiteDatabase _db; //Underlying sqlite database
    private final ExecutorService _appExecutor; //An executor which provides thread on which results from queries will be returned

    public SqlDb(SQLiteOpenHelper helper, ExecutorService appExecutor)
    {
        _db = helper.getWritableDatabase(); //Writable database handles both reads and writes
        _db.execSQL("PRAGMA synchronous=0");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
        {
            /*
            Write ahead logging is available from android api 11 and higher
            and significantly speeds up database operations.
            More info - http://www.sqlite.org/draft/wal.html
            */
            _db.enableWriteAheadLogging();
        }

        _appExecutor = appExecutor;
    }

    /**
     * Sets the current disk synchronization mode which controls how aggressively
     * SQLite will write data to physical storage.
     *
     * @param mode EITHER 1 OR 2
     *              <br><br>
     *              1 - NORMAL MODE, syncs after each sequence of critical disk operations
     *              <br>
     *              2 - FULL MODE, syncs after each critical disk operation
     */
    public void enableSync(int mode)
    {
        if (mode == 1)
        {
            _db.execSQL("PRAGMA synchronous=1");
        } else if (mode == 2)
        {
            _db.execSQL("PRAGMA synchronous=2");
        } else
        {
            throw new IllegalArgumentException("Invalid synchronous pragma value " + mode);
        }
    }

    public interface ITransactionCompleteCallback
    {
        /*
        A simple callback which is required by the the runInTransaction
        method to inform the app that the
        transaction has completed
        */
        void onComplete(boolean b);
    }

    public interface IQueryProcessor
    {
        public Cursor process(QueryParams queryParam);
    }

    /*
    Query methods: These methods provide access to a cursor via the
    CursorHandler. The execution of the cursor is done
    by calling the CursorHandler.handle method in the db reader thread. The
    result of the CursorHandler.handle method
    is then made available to the app in the app provided executor thread via
     the CursorHandler.callback method -
    this ensures that the app does not keep the reader thread busy and that
    it becomes available for other read operations.

    All these methods return a Future, instead of a void,
    and this future can be used by the calling thread to wait till
    a result is available. This provides, some sort of synchronous support in
     this otherwise async library.
     */

    public <RESULT> Future<RESULT> rawQuery(final String sql,
                                            final String[] selectionArgs,
                                            final CursorHandler<RESULT> handler)
    {
        final Later<RESULT> l = new Later<RESULT>();
        ScheduledFuture<?> f = SqlDBThreads.scheduleOnReaderDBExecutor(new Runnable()
        {
            @Override
            public void run()
            {
                final Cursor cursor = _db.rawQuery(sql, selectionArgs);
                final RESULT result = handler.handle(cursor);
                closeCursor(cursor);
                l.set(result);
                _appExecutor.submit(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        handler.callback(result);
                    }
                });
            }
        });
        l.wrap(f);
        return l;
    }

    public <RESULT> Future<RESULT> batchQuery(final MultipleCursorHandler<RESULT> bcc,
                                              final List<QueryParams> params)
    {
        //For running a bunch of queries that return results  of the same type

        final Later<RESULT> l = new Later<RESULT>();
        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                Iterator<QueryResult> iter = makeSequentialCursorProcessor(params);
                final RESULT results = bcc.convert(iter);
                l.set(results);
                Runnable rr = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        bcc.callback(results);
                    }
                };
                _appExecutor.submit(rr);
            }
        };
        ScheduledFuture<?> f = SqlDBThreads.scheduleOnReaderDBExecutor(r);
        l.wrap(f);
        return l;
    }

    public <RESULT> Future<RESULT> query(final String table,
                                         final String[] columns,
                                         final String selection,
                                         final String[] selectionArgs,
                                         final String groupBy,
                                         final String having,
                                         final String orderBy,
                                         final String limit,
                                         final CursorHandler<RESULT> handler)
    {
        final Later<RESULT> l = new Later<RESULT>();
        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                Cursor c = syncQuery(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
                final RESULT result = handler.handle(c);
                closeCursor(c);
                l.set(result);
                Runnable rr = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        handler.callback(result);
                    }
                };
                _appExecutor.submit(rr);
            }
        };
        ScheduledFuture<?> f = SqlDBThreads.scheduleOnReaderDBExecutor(r);
        l.wrap(f);
        return l;
    }

    public <RESULT> Future<RESULT> query(final String table,
                                         final String[] columns,
                                         final String selection,
                                         final String[] selectionArgs,
                                         final String groupBy,
                                         final String having,
                                         final String orderBy,
                                         final CursorHandler<RESULT> handler)
    {
        return query(table, columns, selection, selectionArgs, groupBy,
                having, orderBy, null /*limit*/, handler);
    }

    public <RESULT> Future<RESULT> query(final String table,
                                         final String[] columns,
                                         final String selection,
                                         final String[] selectionArgs,
                                         final CursorHandler<RESULT> handler)
    {
        return query(table, columns, selection, selectionArgs, null, null,
                null, handler);
    }

    /*
    Modification methods: These methods execute on a single thread dedicated
    for DB writes. They return the
    number of rows effected via a DBCallback.

    All these methods return a Future, instead of a void,
    and this future can be used by the calling thread to wait till
    a result is available. This provides, some sort of synchronous support in
     this otherwise async library.
     */

    public Future<Integer> delete(final String table,
                                  final String whereClause,
                                  final String[] whereArgs, final DBCallback cb)
    {
        final Later<Integer> l = new Later<Integer>();
        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    int numRows = _db.delete(table, whereClause, whereArgs);
                    l.set(numRows);
                    callbackInAppExecutor(cb, numRows);
                }
                catch (Exception e)
                {
                    errorbackInAppExecutor(cb, e);
                }
            }
        };
        ScheduledFuture<?> f = SqlDBThreads.scheduleOnWriterDBExecutor(r);
        l.wrap(f);
        return l;
    }

    public Future<Long> insertWithOnConflict(final String table,
                                             final String nullColumnHack,
                                             final ContentValues initialValues,
                                             final int conflictAlgorithm,
                                             final DBCallback cb)
    {
        final Later<Long> l = new Later<Long>();
        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    long id = _db.insertWithOnConflict(table, nullColumnHack,
                            initialValues, conflictAlgorithm);
                    l.set(id);
                    callbackInAppExecutor(cb, id);
                }
                catch (Exception e)
                {
                    errorbackInAppExecutor(cb, e);
                }
            }
        };
        ScheduledFuture<?> f = SqlDBThreads.scheduleOnWriterDBExecutor(r);
        l.wrap(f);
        return l;
    }

    public Future<Long> insert(String table, String nullColumnHack,
                               ContentValues values, DBCallback cb)
    {
        return insertWithOnConflict(table, nullColumnHack, values,
                SQLiteDatabase.CONFLICT_NONE, cb);
    }

    public Future<Integer> updateWithOnConflict(final String table,
                                                final ContentValues values,
                                                final String whereClause,
                                                final String[] whereArgs,
                                                final int conflictAlgorithm,
                                                final DBCallback cb)
    {
        final Later<Integer> l = new Later<Integer>();
        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    int numRows = _db.updateWithOnConflict(table, values,
                            whereClause, whereArgs, conflictAlgorithm);
                    l.set(numRows);
                    callbackInAppExecutor(cb, numRows);
                }
                catch (Exception e)
                {
                    errorbackInAppExecutor(cb, e);
                }
            }
        };
        ScheduledFuture<?> f = SqlDBThreads.scheduleOnWriterDBExecutor(r);
        l.wrap(f);
        return l;
    }

    public Future<Integer> update(String table, ContentValues values,
                                  String whereClause, String[] whereArgs,
                                  DBCallback cb)
    {
        return updateWithOnConflict(table, values, whereClause, whereArgs,
                SQLiteDatabase.CONFLICT_NONE, cb);
    }

    public Future<Long> replace(final String table,
                                final String nullColumnHack,
                                final ContentValues initialValues,
                                final DBCallback cb)
    {
        final Later<Long> l = new Later<Long>();
        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    long id = _db.replace(table, nullColumnHack, initialValues);
                    l.set(id);
                    callbackInAppExecutor(cb, id);
                }
                catch (Exception e)
                {
                    errorbackInAppExecutor(cb, e);
                }
            }
        };
        ScheduledFuture<?> f = SqlDBThreads.scheduleOnWriterDBExecutor(r);
        l.wrap(f);
        return l;
    }


    public Later<Boolean> runInTransaction(final Runnable job,
                                           final ITransactionCompleteCallback
                                                   callback)
    {
        /*
         This method executes a runnable inside a transaction and fires a
        callback when the operation is finished.

        WARNING: This method will DEADLOCK if the runnable blocks by using the
        futures that are returned from the methods in this class. To use this
        method properly don't use Future.get inside the runnable. Having
        said that there is no use case that can possibly benefit from
        blocking on the future inside the runnable.
          */

        final Later<Boolean> l = new Later<Boolean>();
        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                    {
                        _db.beginTransactionNonExclusive();
                    }
                    else
                    {
                        _db.beginTransaction();
                    }
                    job.run();
                    _db.setTransactionSuccessful();
                    l.set(true);
                }
                catch (Exception e)
                {
                    l.set(false);
                    fireCompletionCallback(callback, false);
                }
                finally
                {
                    _db.endTransaction();
                }
                fireCompletionCallback(callback, true);
            }
        };
        ScheduledFuture<?> f = SqlDBThreads.scheduleOnWriterDBExecutor(r);
        l.wrap(f);
        return l;
    }

    /* PRIVATES */


    private Cursor syncQuery(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit)
    {
        return _db.query(table, columns, selection, selectionArgs,
                groupBy, having, orderBy, limit);
    }

    private Iterator<QueryResult> makeSequentialCursorProcessor(List<QueryParams> params)
    {
        return new SequentialCursorProcessor
                (new IQueryProcessor()
                {
                    @Override
                    public Cursor process(QueryParams p)
                    {
                        return _db.query(p.getTable(), p.getColumns(),
                                p.getSelection(), p.getSelectionArgs(),
                                p.getGroupBy(), p.getHaving(),
                                p.getOrderBy(), p.getLimit());
                    }
                }, params);
    }

    private void callbackInAppExecutor(final DBCallback cb, final long arg)
    {
        if (cb != null)
        {
            Runnable r = new Runnable()
            {
                @Override
                public void run()
                {
                    cb.exec(arg);
                }
            };
            _appExecutor.submit((r));
        }
    }

    private void errorbackInAppExecutor(final DBCallback cb, final Exception e)
    {
        if (cb != null)
        {
            Runnable r = new Runnable()
            {
                @Override
                public void run()
                {
                    cb.onError(e);
                }
            };
            _appExecutor.submit(r);
        }
    }


    private void fireCompletionCallback(final ITransactionCompleteCallback
                                                cb, final boolean b)
    {
        if (cb != null)
        {
            _appExecutor.submit(new Runnable()
            {
                @Override
                public void run()
                {
                    cb.onComplete(b);
                }
            });
        }
    }

    private void closeCursor(Cursor cursor)
    {
        if (!cursor.isClosed())
        {
            cursor.close();
        }
    }

}
