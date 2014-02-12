sqldb
=====

SqlDb is a wrapper that hides thread and connection management for android SQLite databases and provides both a sync and async api - it is not an ORM. 

To use SqlDb, construct it by providing 

* an android SQLLiteOpenHelper which is used to retreive the underlying database
* an ExecutorService to provide thread(s) on which to return the result of database operations
* a flag to indicate if SQLite SYNCHRONOUS pragma is to be disabled for performance gains, recommended.


After you have a SqlDb instance, you can perform typical database operations with signatures that are very similar to the android provided api - with one difference. All methods in SqlDb take a callback in order to provide an async api.

To use Sqldb.query methods you will need to provide an instance of the CursorHandler interface. This requires you to implement two methods - handle() and callback(). The handle method should have application code that reades a cursor and converts it into some object which is useful for the application. The callback method then receives this application object and uses it. This abstraction is required for two reasons, firstly, to ensure that that the cursor is executed on the same thread as the underlying SQLConnection this is done by calling CursorHandler.handle in the database reader thread, and secondly, to ensure that the appication receives the result on a seprate thread so that the database thread is freed up for other operations - this is done by calling CursorHandler.callback in a thread from the application provided ExecutorService which was injected in the SqlDb constructor. 

Here is an example on how to use a query method, note that Fruit is an application class.

```java
SqlDb db = new SqlDb(sqliteOpenHelper, appExecutorService, false);

CursorHandler<Fruit> handler = new CursorHandler<Fruit>()
{

  @Override
  Fruit handle(Cursor c)
  {
      //Iterate over cursor, extract fields and make a Fruit
      
      return fruit;
  }
  
  @Override
  void callback(Fruit result)
  {
    //Use fruit in app
    String color = result.getColor();
    String name = result.getName();
  }
};

db.query("fruits", columns, selection, selectionArgs, handler); 

```

Methods that write to the db such as 'replace', 'insert' or 'delete' are very much like the ones provided by android and much like the query method they take a simple callback of type DBCallback which is called after the operation is complete. Here is an example

```java
DBCallback callback = new DBCallback()
{
  @Override
  void exec(long l)
  {
    //'l' indicates the number of rows affected by this operation.
  }

  @Override
  void onError(Exception e)
  {
    //called with an exception if one occurs while executing
  }
};

db.replace("fruits", null, initialValues, callback);
```

Note that all methods in SqlDb return a Future\<T\> of the type used in CursorHandler\<T\> for queries and of the type Long for writes. This future is useful to make these async methods sync by allowing the calling application thread to block by calling the Future.get method - which blocks the calling thread till a result is available. For most usecases we will just ignore this Future as we don't want to wait for the result and will instead rely on the callback.

```java
Future<T> f = db.replace(...);
f.get() //This blocks the current thread till SqlDb finishes processing and has a result available

```

There are also methods that allow the execution of 'rawQuery',  'batchQuery' and the usage of transactions.

PS: Please file github issues, for bugs/features/suggestions and pull requests are welcome.


[![githalytics.com alpha](https://cruel-carlota.pagodabox.com/bf60f0d436365b6217d1014f3844e199 "githalytics.com")](http://githalytics.com/kashifrazzaqui/sqldb)
