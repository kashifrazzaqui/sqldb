package prj.sqldb;

import android.database.Cursor;

public interface CursorHandler<MY_TYPE>
{
    /* This method is executed on a DB reader thread
     * Its implementation should convert the Cursor to some type useful for the application
     */
    MY_TYPE handle(Cursor cursor);

    /* This method is called on a thread provided by the ExecutorService that is given to SqlDb in its constructor
     * It provides the application with the result of the work done in the handle() method.
     */
    void callback(MY_TYPE result);
}
