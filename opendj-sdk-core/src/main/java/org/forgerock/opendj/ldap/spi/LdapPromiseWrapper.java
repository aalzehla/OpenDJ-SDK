/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.spi;

import static org.forgerock.opendj.ldap.spi.LdapPromises.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.promise.RuntimeExceptionHandler;

/**
 * Provides a {@link Promise} wrapper and a {@link LdapPromise} implementation.
 *
 *
 * This wrapper allows client code to return {@link LdapPromise} instance when
 * using {@link Promise} chaining methods (e.g thenOnResult(), then(), thenAsync()).
 * Wrapping is specially needed with {@link Promise} method which are not returning the original promise.
 *
 * @param <R>
 *       The type of the task's result.
 * @param <P>
 *       The wrapped promise.
 */
class LdapPromiseWrapper<R, P extends Promise<R, LdapException>> implements LdapPromise<R> {
    private final P wrappedPromise;
    private final int requestID;

    public LdapPromiseWrapper(P wrappedPromise, int requestID) {
        this.wrappedPromise = wrappedPromise;
        this.requestID = requestID;
    }

    @Override
    public int getRequestID() {
        return wrappedPromise instanceof LdapPromise ? ((LdapPromise<R>) wrappedPromise).getRequestID()
                : requestID;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return wrappedPromise.cancel(mayInterruptIfRunning);
    }

    @Override
    public R get() throws ExecutionException, InterruptedException {
        return wrappedPromise.get();
    }

    @Override
    public R get(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException, InterruptedException {
        return wrappedPromise.get(timeout, unit);
    }

    @Override
    public R getOrThrow() throws InterruptedException, LdapException {
        return wrappedPromise.getOrThrow();
    }

    @Override
    public R getOrThrow(long timeout, TimeUnit unit) throws InterruptedException, LdapException, TimeoutException {
        return wrappedPromise.getOrThrow(timeout, unit);
    }

    @Override
    public R getOrThrowUninterruptibly() throws LdapException {
        return wrappedPromise.getOrThrowUninterruptibly();
    }

    @Override
    public R getOrThrowUninterruptibly(long timeout, TimeUnit unit) throws LdapException, TimeoutException {
        return wrappedPromise.getOrThrowUninterruptibly(timeout, unit);
    }

    @Override
    public boolean isCancelled() {
        return wrappedPromise.isCancelled();
    }

    @Override
    public boolean isDone() {
        return wrappedPromise.isDone();
    }

    @Override
    public LdapPromise<R> thenOnException(ExceptionHandler<? super LdapException> onException) {
        wrappedPromise.thenOnException(onException);
        return this;
    }

    @Override
    public LdapPromise<R> thenOnRuntimeException(RuntimeExceptionHandler onRuntimeException) {
        wrappedPromise.thenOnRuntimeException(onRuntimeException);
        return this;
    }

    @Override
    public LdapPromise<R> thenOnResult(ResultHandler<? super R> onResult) {
        wrappedPromise.thenOnResult(onResult);
        return this;
    }

    @Override
    public LdapPromise<R> thenOnResultOrException(Runnable onResultOrException) {
        wrappedPromise.thenOnResultOrException(onResultOrException);
        return this;
    }

    @Override
    // @Checkstyle:ignore
    public <VOUT> LdapPromise<VOUT> then(Function<? super R, VOUT, LdapException> onResult) {
        return wrap(wrappedPromise.then(onResult), getRequestID());
    }

    @Override
    // @Checkstyle:ignore
    public <VOUT, EOUT extends Exception> Promise<VOUT, EOUT> then(Function<? super R, VOUT, EOUT> onResult,
            Function<? super LdapException, VOUT, EOUT> onException) {
        return wrappedPromise.then(onResult, onException);
    }

    @Override
    public LdapPromise<R> thenOnResultOrException(ResultHandler<? super R> onResult,
                                                  ExceptionHandler<? super LdapException> onException) {
        wrappedPromise.thenOnResultOrException(onResult, onException);
        return this;
    }

    @Override
    public LdapPromise<R> thenAlways(Runnable onResultOrException) {
        wrappedPromise.thenAlways(onResultOrException);
        return this;
    }

    @Override
    // @Checkstyle:ignore
    public <VOUT> LdapPromise<VOUT> thenAsync(AsyncFunction<? super R, VOUT, LdapException> onResult) {
        return wrap(wrappedPromise.thenAsync(onResult), getRequestID());
    }

    @Override
    // @Checkstyle:ignore
    public <VOUT, EOUT extends Exception> Promise<VOUT, EOUT> thenAsync(
            AsyncFunction<? super R, VOUT, EOUT> onResult,
            AsyncFunction<? super LdapException, VOUT, EOUT> onException) {
        return wrappedPromise.thenAsync(onResult, onException);
    }

	@Override
	public <VOUT, EOUT extends Exception> Promise<VOUT, EOUT> thenAsync(AsyncFunction<? super R, VOUT, EOUT> onResult,
			AsyncFunction<? super LdapException, VOUT, EOUT> onException,
			AsyncFunction<? super RuntimeException, VOUT, EOUT> onRuntimeException) {
		return wrappedPromise.thenAsync(onResult, onException);
	}
	
    @Override
    // @Checkstyle:ignore
    public <EOUT extends Exception> Promise<R, EOUT> thenCatch(Function<? super LdapException, R, EOUT> onException) {
        return wrappedPromise.thenCatch(onException);
    }

    @Override
    public LdapPromise<R> thenFinally(Runnable onResultOrException) {
        wrappedPromise.thenFinally(onResultOrException);
        return this;
    }

    @Override
    // @Checkstyle:ignore
    public <EOUT extends Exception> Promise<R, EOUT> thenCatchAsync(
            AsyncFunction<? super LdapException, R, EOUT> onException) {
        return wrappedPromise.thenCatchAsync(onException);
    }

    public P getWrappedPromise() {
        return wrappedPromise;
    }

	@Override
	public Promise<R, LdapException> thenCatchRuntimeException(
			Function<? super RuntimeException, R, LdapException> onRuntimeException) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Promise<R, LdapException> thenCatchRuntimeExceptionAsync(
			AsyncFunction<? super RuntimeException, R, LdapException> onRuntimeException) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <VOUT, EOUT extends Exception> Promise<VOUT, EOUT> then(Function<? super R, VOUT, EOUT> onResult,
			Function<? super LdapException, VOUT, EOUT> onException,
			Function<? super RuntimeException, VOUT, EOUT> onRuntimeException) {
		// TODO Auto-generated method stub
		return null;
	}


}
