package prj.sqldb;

import java.util.Iterator;

public interface MultipleCursorHandler<T>
{
    /*
     * This method is executed on a DB reader thread
     * Its implementation should get a cursor by looping over the
     * provided iterator and and obtaining a QueryResult from iter.next()
     * The cursor is available from QueryResult.getCursor(). The cursor
     * should then be used and results converted to some type useful for the application
     */

    T convert(Iterator<QueryResult> iter);

    /*
     * This method is called on a thread provided by the ExecutorService that
     * is given to SqlDb in its constructor
     * It provides the application with the result of the work done in the handle() method.
     */

    void callback(T aggregatedResults);
}
