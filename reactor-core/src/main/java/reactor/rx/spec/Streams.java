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

package reactor.rx.spec;

import org.reactivestreams.Publisher;
import reactor.core.Environment;
import reactor.core.Observable;
import reactor.core.Reactor;
import reactor.event.dispatch.Dispatcher;
import reactor.event.dispatch.SynchronousDispatcher;
import reactor.event.selector.Selector;
import reactor.function.Function;
import reactor.function.Supplier;
import reactor.rx.Stream;
import reactor.rx.action.*;
import reactor.tuple.*;
import reactor.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A public factory to build {@link Stream}.
 *
 * @author Stephane Maldini
 * @author Jon Brisbin
 */
public final class Streams {

	/**
	 * Build a deferred {@literal Stream}, ready to broadcast values.
	 *
	 * @param env the Reactor {@link reactor.core.Environment} to use
	 * @param <T> the type of values passing through the {@literal Stream}
	 * @return a new {@link reactor.rx.Stream}
	 */
	public static <T> Stream<T> defer(Environment env) {
		return defer(env, env.getDefaultDispatcher());
	}

	/**
	 * Build a deferred synchronous {@literal Stream}, ready to broadcast values.
	 *
	 * @param <T> the type of values passing through the {@literal Stream}
	 * @return a new {@link Stream}
	 */
	public static <T> Stream<T> defer() {
		return new Stream<T>();
	}

	/**
	 * Build a deferred {@literal Stream}, ready to broadcast values.
	 *
	 * @param env        the Reactor {@link reactor.core.Environment} to use
	 * @param dispatcher the {@link reactor.event.dispatch.Dispatcher} to use
	 * @param <T>        the type of values passing through the {@literal Stream}
	 * @return a new {@link reactor.rx.Stream}
	 */
	public static <T> Stream<T> defer(Environment env, Dispatcher dispatcher) {
		Assert.state(dispatcher.supportsOrdering(), "Dispatcher provided doesn't support event ordering. " +
				" Refer to #parallel() method. ");
		return new Stream<T>(dispatcher, env, dispatcher.backlogSize() > 0 ?
				(Action.RESERVED_SLOTS > dispatcher.backlogSize() ?
						dispatcher.backlogSize() :
						dispatcher.backlogSize() - Action.RESERVED_SLOTS) :
				Long.MAX_VALUE);
	}


	/**
	 * Build a deferred concurrent {@link ParallelAction}, ready to broadcast values to the generated sub-streams.
	 * This is a MP-MC scenario type where the parallel action dispatches within the calling dispatcher scope. There
	 * is no
	 * intermediate boundary such as with standard stream like str.buffer().parallel(16) where "buffer" action is run
	 * into a dedicated dispatcher.
	 * <p>
	 * A Parallel action will starve its next available sub-stream to capacity before selecting the next one.
	 * <p>
	 * Will default to {@link Environment#PROCESSORS} number of partitions.
	 * Will default to a new {@link reactor.core.Environment#newDispatcherFactory(int)}} supplier.
	 *
	 * @param <T> the type of values passing through the {@literal Stream}
	 * @return a new {@link reactor.rx.Stream} of  {@link reactor.rx.Stream}
	 */
	public static <T> ParallelAction<T> parallel() {
		return parallel(Environment.PROCESSORS);
	}

	/**
	 * Build a deferred concurrent {@link ParallelAction}, ready to broadcast values to the generated sub-streams.
	 * This is a MP-MC scenario type where the parallel action dispatches within the calling dispatcher scope. There
	 * is no
	 * intermediate boundary such as with standard stream like str.buffer().parallel(16) where "buffer" action is run
	 * into a dedicated dispatcher.
	 * <p>
	 * A Parallel action will starve its next available sub-stream to capacity before selecting the next one.
	 * <p>
	 * Will default to {@link Environment#PROCESSORS} number of partitions.
	 * Will default to a new {@link reactor.core.Environment#newDispatcherFactory(int)}} supplier.
	 *
	 * @param <T>      the type of values passing through the {@literal Stream}
	 * @param poolSize the number of maximum parallel sub-streams consuming the broadcasted values.
	 * @return a new {@link reactor.rx.Stream} of  {@link reactor.rx.Stream}
	 */
	public static <T> ParallelAction<T> parallel(int poolSize) {
		return parallel(poolSize, null, Environment.newDispatcherFactory(poolSize));
	}

	/**
	 * Build a deferred concurrent {@link ParallelAction}, ready to broadcast values to the generated sub-streams.
	 * This is a MP-MC scenario type where the parallel action dispatches within the calling dispatcher scope. There
	 * is no
	 * intermediate boundary such as with standard stream like str.buffer().parallel(16) where "buffer" action is run
	 * into a dedicated dispatcher.
	 * <p>
	 * A Parallel action will starve its next available sub-stream to capacity before selecting the next one.
	 * <p>
	 * Will default to {@link reactor.core.Environment#getDefaultDispatcherFactory()} supplier.
	 * Will default to {@link Environment#PROCESSORS} number of partitions.
	 *
	 * @param env the Reactor {@link reactor.core.Environment} to use
	 * @param <T> the type of values passing through the {@literal Stream}
	 * @return a new {@link reactor.rx.Stream} of  {@link reactor.rx.Stream}
	 */
	public static <T> ParallelAction<T> parallel(Environment env) {
		return parallel(Environment.PROCESSORS, env, env.getDefaultDispatcherFactory());
	}

	/**
	 * Build a deferred concurrent {@link ParallelAction}, ready to broadcast values to the generated sub-streams.
	 * This is a MP-MC scenario type where the parallel action dispatches within the calling dispatcher scope. There
	 * is no
	 * intermediate boundary such as with standard stream like str.buffer().parallel(16) where "buffer" action is run
	 * into a dedicated dispatcher.
	 * <p>
	 * A Parallel action will starve its next available sub-stream to capacity before selecting the next one.
	 * <p>
	 * Will default to {@link reactor.core.Environment#getDefaultDispatcherFactory()} supplier.
	 *
	 * @param env      the Reactor {@link reactor.core.Environment} to use
	 * @param poolSize the number of maximum parallel sub-streams consuming the broadcasted values.
	 * @param <T>      the type of values passing through the {@literal Stream}
	 * @return a new {@link reactor.rx.Stream} of  {@link reactor.rx.Stream}
	 */
	public static <T> ParallelAction<T> parallel(int poolSize, Environment env) {
		return parallel(poolSize, env, env.getDefaultDispatcherFactory());
	}

	/**
	 * Build a deferred concurrent {@link ParallelAction}, accepting data signals to broadcast to a selected generated
	 * sub-streams.
	 * This is a MP-MC scenario type where the parallel action dispatches within the calling dispatcher scope. There
	 * is no
	 * intermediate boundary such as with standard stream like str.buffer().parallel(16) where "buffer" action is run
	 * into a dedicated dispatcher.
	 * <p>
	 * A Parallel action will starve its next available sub-stream to capacity before selecting the next one.
	 * <p>
	 * Will default to {@link Environment#PROCESSORS} number of partitions.
	 *
	 * @param env         the Reactor {@link reactor.core.Environment} to use
	 * @param dispatchers the {@link reactor.event.dispatch.Dispatcher} factory to assign each sub-stream with a call to
	 *                    {@link reactor.function.Supplier#get()}
	 * @param <T>         the type of values passing through the {@literal Stream}
	 * @return a new {@link reactor.rx.Stream} of  {@link reactor.rx.Stream}
	 */
	public static <T> ParallelAction<T> parallel(Environment env, Supplier<Dispatcher> dispatchers) {
		return parallel(Environment.PROCESSORS, env, dispatchers);
	}

	/**
	 * Build a deferred concurrent {@link ParallelAction}, ready to broadcast values to the generated sub-streams.
	 * This is a MP-MC scenario type where the parallel action dispatches within the calling dispatcher scope. There
	 * is no
	 * intermediate boundary such as with standard stream like str.buffer().parallel(16) where "buffer" action is run
	 * into a dedicated dispatcher.
	 * A Parallel action will starve its next available sub-stream to capacity before selecting the next one.
	 *
	 * @param poolSize    the number of maximum parallel sub-streams consuming the broadcasted values.
	 * @param env         the Reactor {@link reactor.core.Environment} to use
	 * @param dispatchers the {@link reactor.event.dispatch.Dispatcher} factory to assign each sub-stream with a call to
	 *                    {@link reactor.function.Supplier#get()}
	 * @param <T>         the type of values passing through the {@literal Stream}
	 * @return a new {@link reactor.rx.Stream} of  {@link reactor.rx.Stream}
	 */
	public static <T> ParallelAction<T> parallel(int poolSize, Environment env, Supplier<Dispatcher> dispatchers) {
		ParallelAction<T> parallelAction = new ParallelAction<T>(SynchronousDispatcher.INSTANCE, dispatchers, poolSize);
		parallelAction.env(env);
		return parallelAction;
	}

	/**
	 * Build a synchronous {@literal Stream}, ready to broadcast values from the given publisher. A publisher will start
	 * producing next elements until onComplete is called.
	 *
	 * @param publisher the publisher to broadcast the Stream subscriber
	 * @param <T>       the type of values passing through the {@literal Stream}
	 * @return a new {@link reactor.rx.Stream}
	 */
	public static <T> Stream<T> defer(Publisher<? extends T> publisher) {
		return defer(null, SynchronousDispatcher.INSTANCE, publisher);
	}

	/**
	 * Build a deferred {@literal Stream}, ready to broadcast values from the given publisher. A publisher will start
	 * producing next elements until onComplete is called.
	 *
	 * @param publisher the publisher to broadcast the Stream subscriber
	 * @param env       The assigned environment
	 * @param <T>       the type of values passing through the {@literal Stream}
	 * @return a new {@link reactor.rx.Stream}
	 */
	public static <T> Stream<T> defer(Environment env, Publisher<? extends T> publisher) {
		return defer(env, env.getDefaultDispatcher(), publisher);
	}

	/**
	 * Build a deferred {@literal Stream}, ready to broadcast values from the given publisher. A publisher will start
	 * producing next elements until onComplete is called.
	 *
	 * @param publisher  the publisher to broadcast the Stream subscriber
	 * @param env        The assigned environment
	 * @param dispatcher The dispatcher to to assign to downstream subscribers
	 * @param <T>        the type of values passing through the {@literal Stream}
	 * @return a new {@link reactor.rx.Stream}
	 */
	public static <T> Stream<T> defer(Environment env, Dispatcher dispatcher, Publisher<? extends T> publisher) {
		Assert.state(dispatcher.supportsOrdering(), "Dispatcher provided doesn't support event ordering. To use " +
				"MultiThreadDispatcher, refer to #parallel() method. ");
		Stream<T> stream = defer(env, dispatcher);
		publisher.subscribe(new StreamSpec.StreamSubscriber<T>(stream));
		return stream;
	}

	/**
	 * Return a Specification component to tune the stream properties.
	 *
	 * @param <T> the type of values passing through the {@literal Stream}
	 * @return a new {@link PipelineSpec}
	 */
	public static <T> StreamSpec<T> config() {
		return new StreamSpec<T>();
	}


	/**
	 * Attach a synchronous Stream to the {@link Observable} with the specified {@link Selector}.
	 *
	 * @param observable        the {@link Observable} to observe
	 * @param broadcastSelector the {@link Selector}/{@literal Object} tuple to listen to
	 * @param <T>               the type of values passing through the {@literal Stream}
	 * @return a new {@link Stream}
	 * @since 2.0
	 */
	public static <T> Stream<T> on(Observable observable, Selector broadcastSelector) {
		Dispatcher dispatcher = Reactor.class.isAssignableFrom(observable.getClass()) ?
				((Reactor) observable).getDispatcher() :
				SynchronousDispatcher.INSTANCE;

		Stream<T> stream = defer(null, dispatcher);
		StreamSpec.<T>publisherFrom(observable, broadcastSelector).subscribe(new StreamSpec.StreamSubscriber<T>(stream));
		return stream;
	}

	/**
	 * Build a synchronous {@literal Stream} whose data is generated by the passed supplier on subscription request.
	 * The Stream's batch size will be set to 1.
	 *
	 * @param value The value to {@code broadcast()}
	 * @param <T>   type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> generate(Supplier<? extends T> value) {
		return generate(null, SynchronousDispatcher.INSTANCE, value);
	}

	/**
	 * Build a {@literal Stream} whose data is generated by the passed supplier on subscription request.
	 * The Stream's batch size will be set to 1.
	 *
	 * @param value The value to {@code broadcast()}
	 * @param env   The assigned environment
	 * @param <T>   type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> generate(Environment env, Supplier<? extends T> value) {
		return generate(env, env.getDefaultDispatcher(), value);
	}


	/**
	 * Build a {@literal Stream} whose data is generated by the passed supplier on subscription request.
	 * The Stream's batch size will be set to 1.
	 *
	 * @param value      The value to {@code broadcast()}
	 * @param dispatcher The dispatcher to schedule the flush
	 * @param env        The assigned environment
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> generate(Environment env, Dispatcher dispatcher,
	                                     Supplier<? extends T> value) {
		if (value == null) throw new IllegalArgumentException("Supplier must be provided");
		SupplierAction<Void, T> action = new SupplierAction<Void, T>(dispatcher, value);
		action.capacity(1).env(env);
		return action;
	}


	/**
	 * Build a synchronous {@literal Stream} whom data is sourced by each element of the passed iterable on subscription
	 * request.
	 * If the {@code values} are a {@code Collection} the Stream's batch size will
	 * be set to the Collection's {@link Collection#size()}.
	 *
	 * @param values The values to {@code broadcastNext()}
	 * @param <T>    type of the values
	 * @return a {@link Stream} based on the given values
	 */
	@SafeVarargs
	public static <T> Stream<T> defer(T... values) {
		return defer(Arrays.asList(values));
	}

	/**
	 * Build a synchronous {@literal Stream} whom data is sourced by each element of the passed iterable on subscription
	 * request.
	 * If the {@code values} are a {@code Collection} the Stream's batch size will
	 * be set to the Collection's {@link Collection#size()}.
	 *
	 * @param values The values to {@code broadcastNext()}
	 * @param <T>    type of the values
	 * @return a {@link Stream} based on the given values
	 */
	public static <T> Stream<T> defer(Iterable<? extends T> values) {
		return defer(null, SynchronousDispatcher.INSTANCE, values);
	}

	/**
	 * Build a {@literal Stream} whom data is sourced by each element of the passed iterable on subscription request.
	 * If the {@code values} are a {@code Collection} the Stream's batch size will
	 * be set to the Collection's {@link Collection#size()}.
	 *
	 * @param values The values to {@code broadcast()}
	 * @param env    The assigned environment
	 * @param <T>    type of the values
	 * @return a {@link Stream} based on the given values
	 */
	@SafeVarargs
	public static <T> Stream<T> defer(Environment env, T... values) {
		return defer(env, Arrays.asList(values));
	}

	/**
	 * Build a {@literal Stream} whom data is sourced by each element of the passed iterable on subscription request.
	 * If the {@code values} are a {@code Collection} the Stream's batch size will
	 * be set to the Collection's {@link Collection#size()}.
	 *
	 * @param values The values to {@code broadcast()}
	 * @param env    The assigned environment
	 * @param <T>    type of the values
	 * @return a {@link Stream} based on the given values
	 */
	public static <T> Stream<T> defer(Environment env, Iterable<? extends T> values) {
		return defer(env, env.getDefaultDispatcher(), values);
	}


	/**
	 * Build a {@literal Stream} whom data is sourced by each element of the passed iterable on subscription request.
	 * If the {@code values} are a {@code Collection} the Stream's batch size will
	 * be set to the Collection's {@link Collection#size()}.
	 *
	 * @param values     The values to {@code broadcast()}
	 * @param env        The assigned environment
	 * @param dispatcher The dispatcher to schedule the flush
	 * @param <T>        type of the values
	 * @return a {@link Stream} based on the given values
	 */
	@SafeVarargs
	public static <T> Stream<T> defer(Environment env, Dispatcher dispatcher, T... values) {
		return defer(env, dispatcher, Arrays.asList(values));
	}

	/**
	 * Build a {@literal Stream} whom data is sourced by each element of the passed iterable on subscription request.
	 * If the {@code values} are a {@code Collection} the Stream's batch size will
	 * be set to the Collection's {@link Collection#size()}.
	 *
	 * @param values     The values to {@code broadcast()}
	 * @param env        The assigned environment
	 * @param dispatcher The dispatcher to schedule the flush
	 * @param <T>        type of the values
	 * @return a {@link Stream} based on the given values
	 */
	public static <T> Stream<T> defer(Environment env, Dispatcher dispatcher, Iterable<? extends T> values) {
		Assert.state(dispatcher.supportsOrdering(), "Dispatcher provided doesn't support event ordering. To use " +
				"MultiThreadDispatcher, refer to #parallel() method. ");
		ForEachAction<T> forEachAction = new ForEachAction<T>(values, dispatcher);
		forEachAction.env(env);
		return forEachAction;
	}

	/**
	 * Build a Synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param mergedPublishers The list of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>              type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Iterable<? extends Publisher<? extends T>> mergedPublishers) {
		return merge(null, SynchronousDispatcher.INSTANCE, mergedPublishers);
	}

	/**
	 * Build a Synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param mergedPublishers The publisher of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>              type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T, E extends T> Stream<E> merge(Publisher<? extends Publisher<E>> mergedPublishers) {
		return merge(null, SynchronousDispatcher.INSTANCE, mergedPublishers);
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2
	) {
		return merge(null, SynchronousDispatcher.INSTANCE, Arrays.asList(source1, source2));
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3
	) {
		return merge(null, SynchronousDispatcher.INSTANCE, Arrays.asList(source1, source2, source3));
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4
	) {
		return merge(null, SynchronousDispatcher.INSTANCE, Arrays.asList(source1, source2, source3, source4));
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4,
	                                  Publisher<? extends T> source5
	) {
		return merge(null, SynchronousDispatcher.INSTANCE, Arrays.asList(source1, source2, source3, source4, source5));
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4,
	                                  Publisher<? extends T> source5,
	                                  Publisher<? extends T> source6
	) {
		return merge(null, SynchronousDispatcher.INSTANCE, Arrays.asList(source1, source2, source3, source4, source5,
				source6));
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7 The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4,
	                                  Publisher<? extends T> source5,
	                                  Publisher<? extends T> source6,
	                                  Publisher<? extends T> source7
	) {
		return merge(null, SynchronousDispatcher.INSTANCE, Arrays.asList(source1, source2, source3, source4, source5,
				source6, source7));
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7 The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source8 The eigth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4,
	                                  Publisher<? extends T> source5,
	                                  Publisher<? extends T> source6,
	                                  Publisher<? extends T> source7,
	                                  Publisher<? extends T> source8
	) {
		return merge(null, SynchronousDispatcher.INSTANCE, Arrays.asList(source1, source2, source3, source4, source5,
				source6, source7, source8));
	}


	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env              The assigned environment, The assigned environment, will derive the dispatcher to run on
	 *                          from its {@link reactor
	 *                         .core.Environment#getDefaultDispatcher()}
	 * @param mergedPublishers The list of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>              type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env,
	                                  Iterable<? extends Publisher<? extends T>> mergedPublishers) {
		return merge(env, env.getDefaultDispatcher(), mergedPublishers);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env              The assigned environment, The assigned environment, will derive the dispatcher to run on
	 *                          from its {@link reactor
	 *                         .core.Environment#getDefaultDispatcher()}
	 * @param mergedPublishers The publisher of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>              type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T, E extends T> Stream<E> merge(Environment env,
	                                  Publisher<? extends Publisher<E>> mergedPublishers) {
		return merge(env, env.getDefaultDispatcher(), mergedPublishers);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                .core.Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2
	) {
		return merge(env, env.getDefaultDispatcher(), Arrays.asList(source1, source2));
	}

	/**
	 * Build {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                .core.Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3
	) {
		return merge(env, env.getDefaultDispatcher(), Arrays.asList(source1, source2, source3));
	}

	/**
	 * Build {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                .core.Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4
	) {
		return merge(env, env.getDefaultDispatcher(), Arrays.asList(source1, source2, source3, source4));
	}

	/**
	 * Build {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                .core.Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4,
	                                  Publisher<? extends T> source5
	) {
		return merge(env, env.getDefaultDispatcher(), Arrays.asList(source1, source2, source3, source4, source5));
	}

	/**
	 * Build {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                .core.Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4,
	                                  Publisher<? extends T> source5,
	                                  Publisher<? extends T> source6
	) {
		return merge(env, env.getDefaultDispatcher(), Arrays.asList(source1, source2, source3, source4, source5,
				source6));
	}

	/**
	 * Build {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                .core.Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7 The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4,
	                                  Publisher<? extends T> source5,
	                                  Publisher<? extends T> source6,
	                                  Publisher<? extends T> source7
	) {
		return merge(env, env.getDefaultDispatcher(), Arrays.asList(source1, source2, source3, source4, source5,
				source6, source7));
	}

	/**
	 * Build {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                .core.Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7 The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source8 The eigth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4,
	                                  Publisher<? extends T> source5,
	                                  Publisher<? extends T> source6,
	                                  Publisher<? extends T> source7,
	                                  Publisher<? extends T> source8
	) {
		return merge(env, env.getDefaultDispatcher(), Arrays.asList(source1, source2, source3, source4, source5,
				source6, source7, source8));
	}


	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Dispatcher dispatcher, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2
	) {
		return merge(env, dispatcher, Arrays.asList(source1, source2));
	}

	/**
	 * Build {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                .core.Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Dispatcher dispatcher, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3
	) {
		return merge(env, dispatcher, Arrays.asList(source1, source2, source3));
	}

	/**
	 * Build {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Dispatcher dispatcher, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4
	) {
		return merge(env, dispatcher, Arrays.asList(source1, source2, source3, source4));
	}

	/**
	 * Build {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Dispatcher dispatcher, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4,
	                                  Publisher<? extends T> source5
	) {
		return merge(env, dispatcher, Arrays.asList(source1, source2, source3, source4, source5));
	}

	/**
	 * Build {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5    The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6    The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Dispatcher dispatcher, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4,
	                                  Publisher<? extends T> source5,
	                                  Publisher<? extends T> source6
	) {
		return merge(env, dispatcher, Arrays.asList(source1, source2, source3, source4, source5,
				source6));
	}

	/**
	 * Build {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5    The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6    The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7    The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Dispatcher dispatcher, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4,
	                                  Publisher<? extends T> source5,
	                                  Publisher<? extends T> source6,
	                                  Publisher<? extends T> source7
	) {
		return merge(env, dispatcher, Arrays.asList(source1, source2, source3, source4, source5,
				source6, source7));
	}

	/**
	 * Build {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5    The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6    The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7    The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source8    The eigth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Dispatcher dispatcher, Publisher<? extends T> source1,
	                                  Publisher<? extends T> source2,
	                                  Publisher<? extends T> source3,
	                                  Publisher<? extends T> source4,
	                                  Publisher<? extends T> source5,
	                                  Publisher<? extends T> source6,
	                                  Publisher<? extends T> source7,
	                                  Publisher<? extends T> source8
	) {
		return merge(env, dispatcher, Arrays.asList(source1, source2, source3, source4, source5,
				source6, source7, source8));
	}


	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param mergedPublishers The publisher of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param dispatcher       The dispatcher to run on
	 * @param env              The assigned environment
	 * @param <T>              type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T, E extends T> Stream<E> merge(Environment env, Dispatcher dispatcher,
	                                  Publisher<? extends Publisher<E>> mergedPublishers) {

		final Action<Publisher<E>, E> mergeAction =
				new DynamicMergeAction<E, E>(dispatcher, new MergeAction<E>(dispatcher, null)).env(env);

		mergedPublishers.subscribe(mergeAction);
		return mergeAction;
	}



	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param mergedPublishers The list of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param dispatcher       The dispatcher to run on
	 * @param env              The assigned environment
	 * @param <T>              type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<T> merge(Environment env, Dispatcher dispatcher,
	                                  Iterable<? extends Publisher<? extends T>> mergedPublishers) {
		final List<Publisher<? extends T>> publishers = new ArrayList<>();
		for (Publisher<? extends T> mergedPublisher : mergedPublishers) {
			publishers.add(mergedPublisher);
		}
		final MergeAction<T> mergeAction = new MergeAction<T>(dispatcher, publishers);

		mergeAction.env(env);
		return mergeAction;
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <T1>    type of the value from source1
	 * @param <T2>    type of the value from source2
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, V> Stream<V> zip(Publisher<? extends T1> source1,
	                                        Publisher<? extends T2> source2,
	                                        Function<Tuple2<? extends T1, ? extends T2>, ? extends V> zipper) {
		return zip(null, SynchronousDispatcher.INSTANCE, source1, source2, zipper);
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <T1>    type of the value from source1
	 * @param <T2>    type of the value from source2
	 * @param <T3>    type of the value from source3
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, V> Stream<V> zip(Publisher<? extends T1> source1,
	                                            Publisher<? extends T2> source2,
	                                            Publisher<? extends T3> source3,
	                                            Function<Tuple3<? extends T1, ? extends T2, ? extends T3>,
			                                            ? extends V> zipper) {
		return zip(null, SynchronousDispatcher.INSTANCE, source1, source2, source3, zipper);
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <T1>    type of the value from source1
	 * @param <T2>    type of the value from source2
	 * @param <T3>    type of the value from source3
	 * @param <T4>    type of the value from source4
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, V> Stream<V> zip(Publisher<? extends T1> source1,
	                                                Publisher<? extends T2> source2,
	                                                Publisher<? extends T3> source3,
	                                                Publisher<? extends T4> source4,
	                                                Function<Tuple4<? extends T1, ? extends T2, ? extends T3,
			                                                ? extends T4>,
			                                                V> zipper) {
		return zip(null, SynchronousDispatcher.INSTANCE, source1, source2, source3, source4, zipper);
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <T1>    type of the value from source1
	 * @param <T2>    type of the value from source2
	 * @param <T3>    type of the value from source3
	 * @param <T4>    type of the value from source4
	 * @param <T5>    type of the value from source5
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, T5, V> Stream<V> zip(Publisher<? extends T1> source1,
	                                                    Publisher<? extends T2> source2,
	                                                    Publisher<? extends T3> source3,
	                                                    Publisher<? extends T4> source4,
	                                                    Publisher<? extends T5> source5,
	                                                    Function<Tuple5<? extends T1, ? extends T2, ? extends T3,
			                                                    ? extends T4,
			                                                    ? extends T5>,
			                                                    V> zipper) {
		return zip(null, SynchronousDispatcher.INSTANCE, source1, source2, source3, source4, source5, zipper);
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <T1>    type of the value from source1
	 * @param <T2>    type of the value from source2
	 * @param <T3>    type of the value from source3
	 * @param <T4>    type of the value from source4
	 * @param <T5>    type of the value from source5
	 * @param <T6>    type of the value from source6
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, T5, T6, V> Stream<V> zip(Publisher<? extends T1> source1,
	                                                        Publisher<? extends T2> source2,
	                                                        Publisher<? extends T3> source3,
	                                                        Publisher<? extends T4> source4,
	                                                        Publisher<? extends T5> source5,
	                                                        Publisher<? extends T6> source6,
	                                                        Function<Tuple6<? extends T1, ? extends T2, ? extends T3,
			                                                        ? extends T4, ? extends T5, ? extends T6>,
			                                                        V> zipper) {
		return zip(null, SynchronousDispatcher.INSTANCE, source1, source2, source3, source4, source5, source6, zipper);
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7 The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T1>    type of the value from source1
	 * @param <T2>    type of the value from source2
	 * @param <T3>    type of the value from source3
	 * @param <T4>    type of the value from source4
	 * @param <T5>    type of the value from source5
	 * @param <T6>    type of the value from source6
	 * @param <T7>    type of the value from source7
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, T5, T6, T7, V> Stream<V> zip(Publisher<? extends T1> source1,
	                                                            Publisher<? extends T2> source2,
	                                                            Publisher<? extends T3> source3,
	                                                            Publisher<? extends T4> source4,
	                                                            Publisher<? extends T5> source5,
	                                                            Publisher<? extends T6> source6,
	                                                            Publisher<? extends T7> source7,
	                                                            Function<Tuple7<? extends T1, ? extends T2, ? extends T3,
			                                                            ? extends T4, ? extends T5, ? extends T6,
			                                                            ? extends T7>,
			                                                            V> zipper) {
		return zip(null, SynchronousDispatcher.INSTANCE, source1, source2, source3, source4, source5, source6, source7,
				zipper);
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7 The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source8 The eigth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <T1>    type of the value from source1
	 * @param <T2>    type of the value from source2
	 * @param <T3>    type of the value from source3
	 * @param <T4>    type of the value from source4
	 * @param <T5>    type of the value from source5
	 * @param <T6>    type of the value from source6
	 * @param <T7>    type of the value from source7
	 * @param <T8>    type of the value from source8
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, T5, T6, T7, T8, V> Stream<V> zip(Publisher<? extends T1> source1,
	                                                                Publisher<? extends T2> source2,
	                                                                Publisher<? extends T3> source3,
	                                                                Publisher<? extends T4> source4,
	                                                                Publisher<? extends T5> source5,
	                                                                Publisher<? extends T6> source6,
	                                                                Publisher<? extends T7> source7,
	                                                                Publisher<? extends T8> source8,
	                                                                Function<Tuple8<? extends T1, ? extends T2,
			                                                                ? extends T3,
			                                                                ? extends T4, ? extends T5, ? extends T6,
			                                                                ? extends T7,
			                                                                ? extends T8>, ? extends V> zipper) {
		return zip(null, SynchronousDispatcher.INSTANCE, source1, source2, source3, source4, source5, source6, source7,
				source8, zipper);
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param sources The list of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <V, TUPLE extends Tuple> Stream<V> zip(Iterable<? extends Publisher<?>> sources,
	                                                     Function<TUPLE, ? extends V> zipper) {
		return zip(null, SynchronousDispatcher.INSTANCE, sources, zipper);
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param sources The publisher of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @param <E>     The inner type of {@param source}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <E, V, TUPLE extends Tuple> Stream<V> zip(Publisher<? extends Publisher<E>> sources,
	                                                        Function<TUPLE, ? extends V> zipper) {
		return zip(null, SynchronousDispatcher.INSTANCE, sources, zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, V> Stream<V> zip(Environment env,
	                                        Publisher<? extends T1> source1,
	                                        Publisher<? extends T2> source2,
	                                        Function<Tuple2<? extends T1, ? extends T2>, ? extends V> zipper) {
		return zip(env, env.getDefaultDispatcher(), source1, source2, zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <T1>    type of the value from source1
	 * @param <T2>    type of the value from source2
	 * @param <T3>    type of the value from source3
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, V> Stream<V> zip(Environment env, Publisher<? extends T1> source1,
	                                            Publisher<? extends T2> source2,
	                                            Publisher<? extends T3> source3,
	                                            Function<Tuple3<? extends T1, ? extends T2, ? extends T3>,
			                                            ? extends V> zipper) {
		return zip(env, env.getDefaultDispatcher(), source1, source2, source3, zipper);
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <T1>    type of the value from source1
	 * @param <T2>    type of the value from source2
	 * @param <T3>    type of the value from source3
	 * @param <T4>    type of the value from source4
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, V> Stream<V> zip(Environment env, Publisher<? extends T1> source1,
	                                                Publisher<? extends T2> source2,
	                                                Publisher<? extends T3> source3,
	                                                Publisher<? extends T4> source4,
	                                                Function<Tuple4<? extends T1, ? extends T2, ? extends T3,
			                                                ? extends T4>,
			                                                V> zipper) {
		return zip(env, env.getDefaultDispatcher(), source1, source2, source3, source4, zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <T1>    type of the value from source1
	 * @param <T2>    type of the value from source2
	 * @param <T3>    type of the value from source3
	 * @param <T4>    type of the value from source4
	 * @param <T5>    type of the value from source5
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, T5, V> Stream<V> zip(Environment env, Publisher<? extends T1> source1,
	                                                    Publisher<? extends T2> source2,
	                                                    Publisher<? extends T3> source3,
	                                                    Publisher<? extends T4> source4,
	                                                    Publisher<? extends T5> source5,
	                                                    Function<Tuple5<? extends T1, ? extends T2, ? extends T3,
			                                                    ? extends T4,
			                                                    ? extends T5>,
			                                                    V> zipper) {
		return zip(env, env.getDefaultDispatcher(), source1, source2, source3, source4, source5, zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <T1>    type of the value from source1
	 * @param <T2>    type of the value from source2
	 * @param <T3>    type of the value from source3
	 * @param <T4>    type of the value from source4
	 * @param <T5>    type of the value from source5
	 * @param <T6>    type of the value from source6
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, T5, T6, V> Stream<V> zip(Environment env, Publisher<? extends T1> source1,
	                                                        Publisher<? extends T2> source2,
	                                                        Publisher<? extends T3> source3,
	                                                        Publisher<? extends T4> source4,
	                                                        Publisher<? extends T5> source5,
	                                                        Publisher<? extends T6> source6,
	                                                        Function<Tuple6<? extends T1, ? extends T2, ? extends T3,
			                                                        ? extends T4, ? extends T5, ? extends T6>,
			                                                        V> zipper) {
		return zip(env, env.getDefaultDispatcher(), source1, source2, source3, source4, source5, source6, zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param <T1>    type of the value from source1
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7 The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <T2>    type of the value from source2
	 * @param <T3>    type of the value from source3
	 * @param <T4>    type of the value from source4
	 * @param <T5>    type of the value from source5
	 * @param <T6>    type of the value from source6
	 * @param <T7>    type of the value from source7
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, T5, T6, T7, V> Stream<V> zip(Environment env, Publisher<? extends T1> source1,
	                                                            Publisher<? extends T2> source2,
	                                                            Publisher<? extends T3> source3,
	                                                            Publisher<? extends T4> source4,
	                                                            Publisher<? extends T5> source5,
	                                                            Publisher<? extends T6> source6,
	                                                            Publisher<? extends T7> source7,
	                                                            Function<Tuple7<? extends T1, ? extends T2, ? extends T3,
			                                                            ? extends T4, ? extends T5, ? extends T6,
			                                                            ? extends T7>,
			                                                            V> zipper) {
		return zip(env, env.getDefaultDispatcher(), source1, source2, source3, source4, source5, source6, source7,
				zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7 The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source8 The eigth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper  The aggregate function that will receive a unique value from each upstream and return the
	 *                value to signal downstream
	 * @param <T1>    type of the value from source1
	 * @param <T2>    type of the value from source2
	 * @param <T3>    type of the value from source3
	 * @param <T4>    type of the value from source4
	 * @param <T5>    type of the value from source5
	 * @param <T6>    type of the value from source6
	 * @param <T7>    type of the value from source7
	 * @param <T8>    type of the value from source8
	 * @param <V>     The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, T5, T6, T7, T8, V> Stream<V> zip(Environment env, Publisher<? extends T1> source1,
	                                                                Publisher<? extends T2> source2,
	                                                                Publisher<? extends T3> source3,
	                                                                Publisher<? extends T4> source4,
	                                                                Publisher<? extends T5> source5,
	                                                                Publisher<? extends T6> source6,
	                                                                Publisher<? extends T7> source7,
	                                                                Publisher<? extends T8> source8,
	                                                                Function<Tuple8<? extends T1, ? extends T2,
			                                                                ? extends T3,
			                                                                ? extends T4, ? extends T5, ? extends T6,
			                                                                ? extends T7,
			                                                                ? extends T8>, ? extends V> zipper) {
		return zip(env, env.getDefaultDispatcher(), source1, source2, source3, source4, source5, source6, source7,
				source8, zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param publishers The list of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                   .core.Environment#getDefaultDispatcher()}
	 * @param zipper     The aggregate function that will receive a unique value from each upstream and return the
	 *                   value to signal downstream
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <V, TUPLE extends Tuple> Stream<V> zip(Environment env,
	                                                     Iterable<? extends Publisher<?>> publishers,
	                                                     Function<TUPLE, ? extends V> zipper) {
		return zip(env, env.getDefaultDispatcher(), publishers, zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param publishers The publisher of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                   .core.Environment#getDefaultDispatcher()}
	 * @param zipper     The aggregate function that will receive a unique value from each upstream and return the
	 *                   value to signal downstream
	 * @param <E>        The inner type of {@param source}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <E, V, TUPLE extends Tuple> Stream<V> zip(Environment env,
	                                                        Publisher<? extends Publisher<E>> publishers,
	                                                        Function<TUPLE, ? extends V> zipper) {
		return zip(env, env.getDefaultDispatcher(), publishers, zipper);
	}


	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                   .core.Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper     The aggregate function that will receive a unique value from each upstream and return the
	 *                   value to signal downstream
	 * @param <V>        The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, V> Stream<V> zip(Environment env,
	                                        Dispatcher dispatcher,
	                                        Publisher<? extends T1> source1,
	                                        Publisher<? extends T2> source2,
	                                        Function<Tuple2<? extends T1, ? extends T2>, ? extends V> zipper) {
		return zip(env, dispatcher, Arrays.asList(source1, source2), zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                   .core.Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper     The aggregate function that will receive a unique value from each upstream and return the
	 *                   value to signal downstream
	 * @param <T1>       type of the value from source1
	 * @param <T2>       type of the value from source2
	 * @param <T3>       type of the value from source3
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, V> Stream<V> zip(Environment env,
	                                            Dispatcher dispatcher,
	                                            Publisher<? extends T1> source1,
	                                            Publisher<? extends T2> source2,
	                                            Publisher<? extends T3> source3,
	                                            Function<Tuple3<? extends T1, ? extends T2, ? extends T3>,
			                                            ? extends V> zipper) {
		return zip(env, dispatcher, Arrays.asList(source1, source2, source3), zipper);
	}

	/**
	 * Build a synchronous {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                   .core.Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper     The aggregate function that will receive a unique value from each upstream and return the
	 *                   value to signal downstream
	 * @param <T1>       type of the value from source1
	 * @param <T2>       type of the value from source2
	 * @param <T3>       type of the value from source3
	 * @param <T4>       type of the value from source4
	 * @param <V>        The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, V> Stream<V> zip(Environment env,
	                                                Dispatcher dispatcher,
	                                                Publisher<? extends T1> source1,
	                                                Publisher<? extends T2> source2,
	                                                Publisher<? extends T3> source3,
	                                                Publisher<? extends T4> source4,
	                                                Function<Tuple4<? extends T1, ? extends T2, ? extends T3,
			                                                ? extends T4>,
			                                                V> zipper) {
		return zip(env, dispatcher, Arrays.asList(source1, source2, source3, source4), zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                   .core.Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5    The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper     The aggregate function that will receive a unique value from each upstream and return the
	 *                   value to signal downstream
	 * @param <T1>       type of the value from source1
	 * @param <T2>       type of the value from source2
	 * @param <T3>       type of the value from source3
	 * @param <T4>       type of the value from source4
	 * @param <T5>       type of the value from source5
	 * @param <V>        The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, T5, V> Stream<V> zip(Environment env,
	                                                    Dispatcher dispatcher,
	                                                    Publisher<? extends T1> source1,
	                                                    Publisher<? extends T2> source2,
	                                                    Publisher<? extends T3> source3,
	                                                    Publisher<? extends T4> source4,
	                                                    Publisher<? extends T5> source5,
	                                                    Function<Tuple5<? extends T1, ? extends T2, ? extends T3,
			                                                    ? extends T4,
			                                                    ? extends T5>,
			                                                    V> zipper) {
		return zip(env, dispatcher, Arrays.asList(source1, source2, source3, source4, source5), zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                   .core.Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5    The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6    The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper     The aggregate function that will receive a unique value from each upstream and return the
	 *                   value to signal downstream
	 * @param <T1>       type of the value from source1
	 * @param <T2>       type of the value from source2
	 * @param <T3>       type of the value from source3
	 * @param <T4>       type of the value from source4
	 * @param <T5>       type of the value from source5
	 * @param <T6>       type of the value from source6
	 * @param <V>        The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, T5, T6, V> Stream<V> zip(Environment env,
	                                                        Dispatcher dispatcher,
	                                                        Publisher<? extends T1> source1,
	                                                        Publisher<? extends T2> source2,
	                                                        Publisher<? extends T3> source3,
	                                                        Publisher<? extends T4> source4,
	                                                        Publisher<? extends T5> source5,
	                                                        Publisher<? extends T6> source6,
	                                                        Function<Tuple6<? extends T1, ? extends T2, ? extends T3,
			                                                        ? extends T4, ? extends T5, ? extends T6>,
			                                                        V> zipper) {
		return zip(env, dispatcher, Arrays.asList(source1, source2, source3, source4, source5, source6), zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                   .core.Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5    The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6    The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7    The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper     The aggregate function that will receive a unique value from each upstream and return the
	 *                   value to signal downstream
	 * @param <T1>       type of the value from source1
	 * @param <T2>       type of the value from source2
	 * @param <T3>       type of the value from source3
	 * @param <T4>       type of the value from source4
	 * @param <T5>       type of the value from source5
	 * @param <T6>       type of the value from source6
	 * @param <T7>       type of the value from source7
	 * @param <V>        The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, T5, T6, T7, V> Stream<V> zip(Environment env,
	                                                            Dispatcher dispatcher,
	                                                            Publisher<? extends T1> source1,
	                                                            Publisher<? extends T2> source2,
	                                                            Publisher<? extends T3> source3,
	                                                            Publisher<? extends T4> source4,
	                                                            Publisher<? extends T5> source5,
	                                                            Publisher<? extends T6> source6,
	                                                            Publisher<? extends T7> source7,
	                                                            Function<Tuple7<? extends T1, ? extends T2, ? extends T3,
			                                                            ? extends T4, ? extends T5, ? extends T6,
			                                                            ? extends T7>,
			                                                            V> zipper) {
		return zip(env, dispatcher, Arrays.asList(source1, source2, source3, source4, source5, source6, source7),
				zipper);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor
	 *                   .core.Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5    The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6    The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7    The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source8    The eigth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param zipper     The aggregate function that will receive a unique value from each upstream and return the
	 *                   value to signal downstream
	 * @param <T1>       type of the value from source1
	 * @param <T2>       type of the value from source2
	 * @param <T3>       type of the value from source3
	 * @param <T4>       type of the value from source4
	 * @param <T5>       type of the value from source5
	 * @param <T6>       type of the value from source6
	 * @param <T7>       type of the value from source7
	 * @param <T8>       type of the value from source8
	 * @param <V>        The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T1, T2, T3, T4, T5, T6, T7, T8, V> Stream<V> zip(Environment env,
	                                                                Dispatcher dispatcher,
	                                                                Publisher<? extends T1> source1,
	                                                                Publisher<? extends T2> source2,
	                                                                Publisher<? extends T3> source3,
	                                                                Publisher<? extends T4> source4,
	                                                                Publisher<? extends T5> source5,
	                                                                Publisher<? extends T6> source6,
	                                                                Publisher<? extends T7> source7,
	                                                                Publisher<? extends T8> source8,
	                                                                Function<Tuple8<? extends T1, ? extends T2,
			                                                                ? extends T3,
			                                                                ? extends T4, ? extends T5, ? extends T6,
			                                                                ? extends T7,
			                                                                ? extends T8>, ? extends V> zipper) {
		return zip(env, dispatcher,
				Arrays.asList(source1, source2, source3, source4, source5, source6, source7, source8),
				zipper);
	}


	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param mergedPublishers The list of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param env              The assigned environment
	 * @param dispatcher       The dispatcher to run on
	 * @param zipper           The aggregate function that will receive a unique value from each upstream and return the
	 *                         value to signal downstream
	 * @param <V>              The produced output after transformation by {@param zipper}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <V, TUPLE extends Tuple> Stream<V> zip(Environment env,
	                                                     Dispatcher dispatcher,
	                                                     Iterable<? extends Publisher<?>> mergedPublishers,
	                                                     Function<TUPLE, ? extends V> zipper) {

		return new ZipAction<>(dispatcher, zipper, mergedPublishers).env(env);
	}

	/**
	 * Build a {@literal Stream} whose data are generated by the passed publishers.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source     The publisher of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param env        The assigned environment
	 * @param dispatcher The dispatcher to run on
	 * @param zipper     The aggregate function that will receive a unique value from each upstream and return the
	 *                   value to signal downstream
	 * @param <V>        The produced output after transformation by {@param zipper}
	 * @param <E>        The inner type of {@param source}
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <E, V, TUPLE extends Tuple> Stream<V> zip(Environment env,
	                                                        Dispatcher dispatcher,
	                                                        Publisher<? extends Publisher<E>> source,
	                                                        Function<TUPLE, ? extends V> zipper) {

		final Action<Publisher<E>, V> mergeAction =
				new DynamicMergeAction<E, V>(dispatcher, new ZipAction<E, V, TUPLE>(dispatcher, zipper, null));

		source.subscribe(mergeAction);

		return mergeAction.env(env);
	}

	/**
	 * Build a Synchronous {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2) {
		return join(null, SynchronousDispatcher.INSTANCE, Arrays.asList(source1, source2));
	}


	/**
	 * Build a Synchronous {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3) {
		return join(null, SynchronousDispatcher.INSTANCE, Arrays.asList(source1, source2, source3));
	}

	/**
	 * Build a Synchronous {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4) {
		return join(null, SynchronousDispatcher.INSTANCE, Arrays.asList(source1, source2, source3, source4));
	}

	/**
	 * Build a Synchronous {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4,
	                                       Publisher<? extends T> source5) {
		return join(null, SynchronousDispatcher.INSTANCE, Arrays.asList(source1, source2, source3, source4, source5));
	}

	/**
	 * Build a Synchronous {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4,
	                                       Publisher<? extends T> source5,
	                                       Publisher<? extends T> source6) {
		return join(null, SynchronousDispatcher.INSTANCE, Arrays.asList(source1, source2, source3, source4, source5,
				source6));
	}

	/**
	 * Build a Synchronous {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4,
	                                       Publisher<? extends T> source5,
	                                       Publisher<? extends T> source6,
	                                       Publisher<? extends T> source7) {
		return join(null, SynchronousDispatcher.INSTANCE,
				Arrays.asList(source1, source2, source3, source4, source5, source6, source7));
	}

	/**
	 * Build a Synchronous {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7 The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source8 The eigth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4,
	                                       Publisher<? extends T> source5,
	                                       Publisher<? extends T> source6,
	                                       Publisher<? extends T> source7,
	                                       Publisher<? extends T> source8) {
		return join(null, SynchronousDispatcher.INSTANCE,
				Arrays.asList(source1, source2, source3, source4, source5, source6, source7, source8));
	}

	/**
	 * Build a Synchronous {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param sources The list of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Iterable<? extends Publisher<? extends T>> sources) {
		return join(null, SynchronousDispatcher.INSTANCE, sources);
	}

	/**
	 * Build a Synchronous {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param source The publisher of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>    type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Publisher<? extends Publisher<T>> source) {
		return join(null, SynchronousDispatcher.INSTANCE, source);
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2) {
		return join(env, env.getDefaultDispatcher(), Arrays.asList(source1, source2));
	}


	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3) {
		return join(env, env.getDefaultDispatcher(), Arrays.asList(source1, source2, source3));
	}

	/**
	 * Build a Synchronous {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4) {
		return join(env, env.getDefaultDispatcher(), Arrays.asList(source1, source2, source3, source4));
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4,
	                                       Publisher<? extends T> source5) {
		return join(env, env.getDefaultDispatcher(), Arrays.asList(source1, source2, source3, source4, source5));
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4,
	                                       Publisher<? extends T> source5,
	                                       Publisher<? extends T> source6) {
		return join(env, env.getDefaultDispatcher(), Arrays.asList(source1, source2, source3, source4, source5,
				source6));
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4,
	                                       Publisher<? extends T> source5,
	                                       Publisher<? extends T> source6,
	                                       Publisher<? extends T> source7) {
		return join(env, env.getDefaultDispatcher(),
				Arrays.asList(source1, source2, source3, source4, source5, source6, source7));
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param source1 The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2 The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3 The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4 The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5 The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6 The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7 The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source8 The eigth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4,
	                                       Publisher<? extends T> source5,
	                                       Publisher<? extends T> source6,
	                                       Publisher<? extends T> source7,
	                                       Publisher<? extends T> source8) {
		return join(env, env.getDefaultDispatcher(),
				Arrays.asList(source1, source2, source3, source4, source5, source6, source7, source8));
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env     The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                .Environment#getDefaultDispatcher()}
	 * @param sources The list of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>     type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env, Iterable<? extends Publisher<? extends T>> sources) {
		return join(env, env.getDefaultDispatcher(), sources);
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env    The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *               .Environment#getDefaultDispatcher()}
	 * @param source The publisher of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>    type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env, Publisher<? extends Publisher<T>> source) {
		return join(env, env.getDefaultDispatcher(), source);
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                   .Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env, Dispatcher dispatcher,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2) {
		return join(env, dispatcher, Arrays.asList(source1, source2));
	}


	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                   .Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env, Dispatcher dispatcher,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3) {
		return join(env, dispatcher, Arrays.asList(source1, source2, source3));
	}

	/**
	 * Build a Synchronous {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                   .Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env, Dispatcher dispatcher,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4) {
		return join(env, dispatcher, Arrays.asList(source1, source2, source3, source4));
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                   .Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env, Dispatcher dispatcher,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4,
	                                       Publisher<? extends T> source5) {
		return join(env, dispatcher, Arrays.asList(source1, source2, source3, source4, source5));
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                   .Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5    The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6    The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env, Dispatcher dispatcher,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4,
	                                       Publisher<? extends T> source5,
	                                       Publisher<? extends T> source6) {
		return join(env, dispatcher, Arrays.asList(source1, source2, source3, source4, source5,
				source6));
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                   .Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5    The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6    The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env, Dispatcher dispatcher,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4,
	                                       Publisher<? extends T> source5,
	                                       Publisher<? extends T> source6,
	                                       Publisher<? extends T> source7) {
		return join(env, dispatcher,
				Arrays.asList(source1, source2, source3, source4, source5, source6, source7));
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                   .Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source1    The first upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source2    The second upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source3    The third upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source4    The fourth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source5    The fifth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source6    The sixth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source7    The seventh upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param source8    The eigth upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env, Dispatcher dispatcher,
	                                       Publisher<? extends T> source1,
	                                       Publisher<? extends T> source2,
	                                       Publisher<? extends T> source3,
	                                       Publisher<? extends T> source4,
	                                       Publisher<? extends T> source5,
	                                       Publisher<? extends T> source6,
	                                       Publisher<? extends T> source7,
	                                       Publisher<? extends T> source8) {
		return join(env, dispatcher,
				Arrays.asList(source1, source2, source3, source4, source5, source6, source7, source8));
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                   .Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param sources    The list of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	@SuppressWarnings("unchecked")
	public static <T> Stream<List<T>> join(Environment env, Dispatcher dispatcher,
	                                       Iterable<? extends Publisher<? extends T>> sources) {
		return zip(env, dispatcher, sources, Streams.<T, TupleN>joinFunction());
	}

	/**
	 * Build a {@literal Stream} whose data are aggregated from the passed publishers
	 * (1 element consumed for each merged publisher. resulting in an array of size of {@param mergedPublishers}.
	 * The Stream's batch size will be set to {@literal Long.MAX_VALUE} or the minimum capacity allocated to any
	 * eventual {@link Stream} publisher type.
	 *
	 * @param env        The assigned environment, will derive the dispatcher to run on from its {@link reactor.core
	 *                   .Environment#getDefaultDispatcher()}
	 * @param dispatcher The dispatcher to run on
	 * @param source     The publisher of upstream {@link org.reactivestreams.Publisher} to subscribe to.
	 * @param <T>        type of the value
	 * @return a {@link Stream} based on the produced value
	 * @since 2.0
	 */
	public static <T> Stream<List<T>> join(Environment env,
	                                       Dispatcher dispatcher,
	                                       Publisher<? extends Publisher<T>> source) {
		return zip(env, dispatcher, source, Streams.<T, TupleN>joinFunction());
	}

	@SuppressWarnings("unchecked")
	private static <T, TUPLE extends Tuple> Function<TUPLE, List<T>> joinFunction() {
		return new Function<TUPLE, List<T>>() {
			@Override
			public List<T> apply(TUPLE ts) {
				return Arrays.asList((T[]) ts.toArray());
			}
		};
	}
}
