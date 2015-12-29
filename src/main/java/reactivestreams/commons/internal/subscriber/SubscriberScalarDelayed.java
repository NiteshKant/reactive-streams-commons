/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactivestreams.commons.internal.subscriber;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactivestreams.commons.internal.support.SubscriptionHelper;

public class SubscriberScalarDelayed<I, O> implements Subscriber<I>, Subscription {

	static final int SDS_NO_REQUEST_NO_VALUE   = 0;
	static final int SDS_NO_REQUEST_HAS_VALUE  = 1;
	static final int SDS_HAS_REQUEST_NO_VALUE  = 2;
	static final int SDS_HAS_REQUEST_HAS_VALUE = 3;

	protected final Subscriber<? super O> subscriber;

	protected O value;

	volatile int state;
	@SuppressWarnings("rawtypes")
	static final AtomicIntegerFieldUpdater<SubscriberScalarDelayed> STATE =
			AtomicIntegerFieldUpdater.newUpdater(SubscriberScalarDelayed.class, "state");

	public SubscriberScalarDelayed(Subscriber<? super O> subscriber) {
		this.subscriber = subscriber;
	}

	@Override
	public void request(long n) {
		if (SubscriptionHelper.validate(n)) {
			for (; ; ) {
				int s = sdsGetState();
				if (s == SDS_HAS_REQUEST_NO_VALUE || s == SDS_HAS_REQUEST_HAS_VALUE) {
					return;
				}
				if (s == SDS_NO_REQUEST_HAS_VALUE) {
					if (sdsCasState(SDS_NO_REQUEST_HAS_VALUE, SDS_HAS_REQUEST_HAS_VALUE)) {
						Subscriber<? super O> a = sdsGetSubscriber();
						a.onNext(sdsGetValue());
						a.onComplete();
					}
					return;
				}
				if (sdsCasState(SDS_NO_REQUEST_NO_VALUE, SDS_HAS_REQUEST_NO_VALUE)) {
					return;
				}
			}
		}
	}

	@Override
	public void cancel() {
		sdsSetState(SDS_HAS_REQUEST_HAS_VALUE);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onNext(I t) {
		value = (O) t;
	}

	@Override
	public void onError(Throwable t) {
		subscriber.onError(t);
	}

	@Override
	public void onSubscribe(Subscription s) {
		//if upstream
	}

	@Override
	public void onComplete() {
		subscriber.onComplete();
	}

	public final void sdsSet(O value) {
		Objects.requireNonNull(value);
		for (; ; ) {
			int s = sdsGetState();
			if (s == SDS_NO_REQUEST_HAS_VALUE || s == SDS_HAS_REQUEST_HAS_VALUE) {
				return;
			}
			if (s == SDS_HAS_REQUEST_NO_VALUE) {
				if (sdsCasState(SDS_HAS_REQUEST_NO_VALUE, SDS_HAS_REQUEST_HAS_VALUE)) {
					Subscriber<? super O> a = sdsGetSubscriber();
					a.onNext(value);
					a.onComplete();
				}
				return;
			}
			sdsSetValue(value);
			if (sdsCasState(SDS_NO_REQUEST_NO_VALUE, SDS_NO_REQUEST_HAS_VALUE)) {
				return;
			}
		}
	}

	public final boolean isCancelled() {
		return sdsGetState() == SDS_HAS_REQUEST_HAS_VALUE;
	}

	public final int sdsGetState() {
		return state;
	}

	public final void sdsSetState(int updated) {
		state = updated;
	}

	public final boolean sdsCasState(int expected, int updated) {
		return STATE.compareAndSet(this, expected, updated);
	}

	public O sdsGetValue() {
		return value;
	}

	public void sdsSetValue(O value) {
		this.value = value;
	}

	public final Subscriber<? super O> sdsGetSubscriber() {
		return subscriber;
	}

	public final void set(O value) {
		sdsSet(value);
	}
}