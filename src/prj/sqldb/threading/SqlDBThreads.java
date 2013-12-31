package prj.sqldb.threading;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SqlDBThreads
{
    private static ScheduledExecutorService _dbWriter = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledExecutorService _dbReader = Executors.newSingleThreadScheduledExecutor();

    public static ScheduledFuture<?> scheduleOnWriterDBExecutor(Runnable runnable)
    {
        ExceptionThrowingFutureTask task = new ExceptionThrowingFutureTask(runnable);
        return _dbWriter.schedule(task, 0, TimeUnit.MILLISECONDS);
    }

    public static ScheduledFuture<?> scheduleOnReaderDBExecutor(Runnable runnable)
    {
        ExceptionThrowingFutureTask task = new ExceptionThrowingFutureTask(runnable);
        return _dbReader.schedule(task, 0, TimeUnit.MILLISECONDS);
    }
}
