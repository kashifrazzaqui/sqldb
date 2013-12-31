package prj.sqldb.threading;

import java.util.concurrent.FutureTask;

public class ExceptionThrowingFutureTask extends FutureTask<Object>
{
    public ExceptionThrowingFutureTask(Runnable r)
    {
        super(r, null);
    }

    @Override
    protected void done()
    {
        try
        {
            if (!isCancelled())
            {
                get();
            }
        }
        catch (final Exception e)
        {
            /* This is done to ensure that the exception is not eaten by the executor */
            Thread thread = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    throw new RuntimeException(e.getMessage(),e);
                }
            });
            thread.start();
        }
    }
}
