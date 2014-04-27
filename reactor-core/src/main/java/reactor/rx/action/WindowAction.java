/*
 * Copyright (c) 2011-2013 GoPivotal, Inc. All Rights Reserved.
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
package reactor.rx.action;

import reactor.event.dispatch.Dispatcher;
import reactor.event.registry.Registration;
import reactor.function.Consumer;
import reactor.rx.Stream;
import reactor.timer.Timer;
import reactor.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WindowAction is collecting events on a steam until {@param period} is reached,
 * after that streams collected events further, clears the internal collection and
 * starts collecting items from empty list.
 *
 * @author Stephane Maldini
 * @since 1.1
 */
public class WindowAction<T> extends Action<T,List<T>> {

	private final ReentrantLock lock            = new ReentrantLock();
	private final List<T>       collectedWindow = new ArrayList<T>();
	private final Registration<? extends Consumer<Long>> timerRegistration;


	@SuppressWarnings("unchecked")
	public WindowAction(Dispatcher dispatcher,
	                    Timer timer,
	                    int period, TimeUnit timeUnit, int delay
  ) {
		super(dispatcher);
		Assert.state(timer != null, "Timer must be supplied");
		this.timerRegistration = timer.schedule(new Consumer<Long>() {
			@Override
			public void accept(Long aLong) {
				doWindow(aLong);
			}
		}, period, timeUnit, delay);
	}

	@Override
	public Stream<List<T>> prefetch(int elements) {
		return super.prefetch(elements > 0 ? elements : Integer.MAX_VALUE);
	}

	protected void doWindow(Long aLong) {
		lock.lock();
		try {
			if(!collectedWindow.isEmpty()){
				broadcastNext(new ArrayList<T>(collectedWindow));
				collectedWindow.clear();
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	protected void doNext(T value) {
		lock.lock();
		try {
			collectedWindow.add(value);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public WindowAction<T> cancel() {
		timerRegistration.cancel();
		return (WindowAction<T>)super.cancel();
	}

	@Override
	public WindowAction<T> pause() {
		timerRegistration.pause();
		return (WindowAction<T>)super.pause();
	}

	@Override
	public WindowAction<T> resume() {
		timerRegistration.resume();
		return (WindowAction<T>)super.resume();
	}

	@Override
	protected void doFlush() {
		doWindow(-1l);
	}

}
