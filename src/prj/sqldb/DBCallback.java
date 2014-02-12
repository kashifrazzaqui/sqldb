package prj.sqldb;

public abstract class DBCallback
{
    abstract public void exec(long l); //long 'l' is the number of rows affected.

    public void onError(Exception e) {}
}
