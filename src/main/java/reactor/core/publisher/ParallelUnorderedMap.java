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
package reactor.core.publisher;

import java.util.function.Function;

import org.reactivestreams.*;

import reactor.core.Exceptions;

/**
 * Maps each 'rail' of the source ParallelFlux with a mapper function.
 *
 * @param <T> the input value type
 * @param <R> the output value type
 */
final class ParallelUnorderedMap<T, R> extends ParallelFlux<R> {

	final ParallelFlux<T> source;
	
	final Function<? super T, ? extends R> mapper;
	
	public ParallelUnorderedMap(ParallelFlux<T> source, Function<? super T, ? extends R> mapper) {
		this.source = source;
		this.mapper = mapper;
	}

	@Override
	public void subscribe(Subscriber<? super R>[] subscribers) {
		if (!validate(subscribers)) {
			return;
		}
		
		int n = subscribers.length;
		@SuppressWarnings("unchecked")
		Subscriber<? super T>[] parents = new Subscriber[n];
		
		for (int i = 0; i < n; i++) {
			parents[i] = new ParallelMapSubscriber<>(subscribers[i], mapper);
		}
		
		source.subscribe(parents);
	}

	@Override
	public int parallelism() {
		return source.parallelism();
	}

	@Override
	public boolean isOrdered() {
		return false;
	}
	
	static final class ParallelMapSubscriber<T, R> implements Subscriber<T>, Subscription {

		final Subscriber<? super R> actual;
		
		final Function<? super T, ? extends R> mapper;
		
		Subscription s;
		
		boolean done;
		
		public ParallelMapSubscriber(Subscriber<? super R> actual, Function<? super T, ? extends R> mapper) {
			this.actual = actual;
			this.mapper = mapper;
		}

		@Override
		public void request(long n) {
			s.request(n);
		}

		@Override
		public void cancel() {
			s.cancel();
		}

		@Override
		public void onSubscribe(Subscription s) {
			if (Operators.validate(this.s, s)) {
				this.s = s;
				
				actual.onSubscribe(this);
			}
		}

		@Override
		public void onNext(T t) {
			if (done) {
				return;
			}
			R v;
			
			try {
				v = mapper.apply(t);
			} catch (Throwable ex) {
				Exceptions.throwIfFatal(ex);
				cancel();
				onError(Exceptions.unwrap(ex));
				return;
			}
			
			if (v == null) {
				cancel();
				onError(new NullPointerException("The mapper returned a null value"));
				return;
			}
			
			actual.onNext(v);
		}

		@Override
		public void onError(Throwable t) {
			if (done) {
				Exceptions.onErrorDropped(t);
				return;
			}
			done = true;
			actual.onError(t);
		}

		@Override
		public void onComplete() {
			if (done) {
				return;
			}
			done = true;
			actual.onComplete();
		}
		
	}
}
