package prj.sqldb.threading;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Later<RESULT> implements Future<RESULT>
{
    /* An implementaion of Future<?> that can wrap another future */

    private final Lock _lock;
    private final Condition _condition;
    private RESULT _value;
    private Future<?> _inner;
    private boolean _cancelled;
    private boolean _valueSet;

    public Later()
    {
        _lock = new ReentrantLock();
        _condition = _lock.newCondition();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        if (_inner == null)
        {
            _cancelled = true;
            return false;
        }
        else
        {
            return _inner.cancel(mayInterruptIfRunning);
        }
    }

    @Override
    public boolean isCancelled()
    {
        if (_inner == null)
        {
            return _cancelled;
        }
        else
        {
            return _inner.isCancelled();
        }
    }

    @Override
    public boolean isDone()
    {
        if (_inner == null)
        {
            return _value != null;

        }
        else
        {
            return _inner.isDone();
        }
    }

    @Override
    public RESULT get() throws InterruptedException, ExecutionException
    {
        _lock.lock();
        try
        {
            if (!_valueSet)
            {
                if (_inner == null)
                {
                    _condition.await();
                }
                else
                {
                    _inner.get();
                    get();
                }
            }
            return _value;
        }
        finally
        {
            _lock.unlock();
        }
    }

    @Override
    public RESULT get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
    {
        _lock.lock();
        try
        {
            if (_value == null)
            {
                if (_inner == null)
                {
                    _condition.await(timeout, unit);
                }
                else
                {
                    _inner.get();
                }
            }
            if (_value == null)
            {
                throw new TimeoutException();
            }
            else
            {
                return _value;
            }
        }
        finally
        {
            _lock.unlock();
        }
    }

    public boolean set(RESULT value)
    {
        /* Should never be called on same thread as get() */
        if (_valueSet)
        {
            return false;
        }
        else
        {

            _lock.lock();
            try
            {
                _valueSet = true;
                _value = value;
                _condition.signalAll();
                return true;
            }
            finally
            {
                _lock.unlock();
            }
        }
    }

    public void wrap(Future<?> f)
    {
        _inner = f;
    }
}
