package com.aol.cyclops.control;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.reactivestreams.Publisher;

import com.aol.cyclops.data.collections.extensions.CollectionX;
import com.aol.cyclops.data.collections.extensions.standard.ListX;
import com.aol.cyclops.internal.monads.AnyMSeqImpl;
import com.aol.cyclops.internal.monads.AnyMValueImpl;
import com.aol.cyclops.types.EmptyUnit;
import com.aol.cyclops.types.Foldable;
import com.aol.cyclops.types.Functor;
import com.aol.cyclops.types.MonadicValue;
import com.aol.cyclops.types.To;
import com.aol.cyclops.types.Unit;
import com.aol.cyclops.types.Unwrapable;
import com.aol.cyclops.types.anyM.AnyMSeq;
import com.aol.cyclops.types.anyM.AnyMValue;
import com.aol.cyclops.types.anyM.Witness;
import com.aol.cyclops.types.anyM.Witness.completableFuture;
import com.aol.cyclops.types.anyM.Witness.eval;
import com.aol.cyclops.types.anyM.Witness.futureW;
import com.aol.cyclops.types.anyM.Witness.ior;
import com.aol.cyclops.types.anyM.Witness.list;
import com.aol.cyclops.types.anyM.Witness.maybe;
import com.aol.cyclops.types.anyM.Witness.optional;
import com.aol.cyclops.types.anyM.Witness.set;
import com.aol.cyclops.types.anyM.Witness.stream;
import com.aol.cyclops.types.anyM.Witness.streamable;
import com.aol.cyclops.types.anyM.Witness.tryType;
import com.aol.cyclops.types.anyM.Witness.xor;
import com.aol.cyclops.types.anyM.WitnessType;
import com.aol.cyclops.types.extensability.Comprehender;
import com.aol.cyclops.types.futurestream.LazyFutureStream;
import com.aol.cyclops.types.stream.ToStream;
import com.aol.cyclops.util.Optionals;
import com.aol.cyclops.util.function.Lambda;
import com.aol.cyclops.util.function.Predicates;

/**
 * 
 * Wrapper for Any Monad type
 * 
 * There are two subsclass of AnyM - @see {@link AnyMValue} and  @see {@link AnyMSeq}. 
 * AnyMValue is used to represent Monads that wrap a single value such as {@link Optional}, {@link CompletableFuture}, {@link Maybe}, {@link Eval}, {@link Xor}, {@link Try}, {@link Ior}, {@link FeatureToggle}
 * AnyMSeq is used to represent Monads that wrap an aggregation of values such as {@link Stream}, {@link LazyFutureStream}, {@link List}, {@link Set}, {@link Streamable}
 * 
 * Use AnyM to create your monad wrapper.
 * AnyM.fromXXXX methods can create the appropriate AnyM type for a range of known monad types.
 * 
 * <pre>
 * {@code 
 *    AnyMValue<String> monad1 = AnyM.fromOptional(Optional.of("hello"));
 *    
 *    AnyMSeq<String> monad2 = AnyM.fromStream(Stream.of("hello","world"));
 *  
 * }
 * </pre>
 * 
 * Wrapped monads can be unwrapped via the unwrap method, or converted to the desired type via toXXXX methods
 * 
 *
 * 
 * @author johnmcclean
 *
 * @param <T> type data wrapped by the underlying monad
 */
public interface AnyM<W extends WitnessType,T> extends Unwrapable,To<AnyM<W,T>>, EmptyUnit<T>, Unit<T>, Foldable<T>, Functor<T>, ToStream<T>,Publisher<T> {
   
    
    default <U> AnyMSeq<W,U> unitIterator(Iterator<U> U){
        return (AnyMSeq<W,U>)adapter().unitIterator(U);
    }

    default <R> AnyM<W,R> flatMapA(Function<? super T, ? extends AnyM<W,? extends R>> fn){
        return adapter().flatMap(this, fn);
    }
    default <R> AnyM<W,R> map(Function<? super T,? extends R> fn){
        return adapter().map(this, fn);
    }
    default <T> AnyM<W,T> fromIterable(Iterable<T> t){
        return  (AnyM<W,T>)adapter().unitIterator(t.iterator());
    }
    /**
     * Construct a new instanceof AnyM using the type of the underlying wrapped monad
     * 
     * <pre>
     * {@code
     *   AnyM<Integer> ints = AnyM.fromList(Arrays.asList(1,2,3);
     *   AnyM<String> string = ints.unit("hello");
     * }
     * </pre>
     * 
     * @param value to embed inside the monad wrapped by AnyM
     * @return Newly instantated AnyM
     */
    @Override
    default <T> AnyM<W,T> unit(T t){
        return adapter().unit(t);
    }
    
    /**
     * Applicative 'ap' method to use fluently
     * 
     * <pre>
     * {@code 
     *    AnyM<optional,Function<Integer,Integer>> add = AnyM.fromNullable(this::add2);
     *    add.to(AnyM::ap)
     *       .apply(AnyM.ofNullable(10));
     *   
     *    //AnyM[12] //add 2
     * 
     * }
     * </pre>
     * 
     * @param fn Function inside an Applicative
     * @return Function to apply an Applicative's value to function
     */
    public static <W extends WitnessType,T,R> Function<AnyM<W,T>,AnyM<W,R>> ap(AnyM<W, Function<T,R>> fn){
        return apply->apply.adapter().ap(fn,apply);
    }
    /**
     * Applicative ap2 method to use fluently to apply to a curried function
     * <pre>
     * {@code 
     *    AnyM<optional,Function<Integer,Function<Integer,Integer>>> add = AnyM.fromNullable(Curry.curry2(this::add));
     *    add.to(AnyM::ap2)
     *       .apply(AnyM.ofNullable(10),AnyM.ofNullable(20));
     *   
     *    //AnyM[30] //add together
     * 
     * }
     * </pre>
     * @param fn Curried function inside an Applicative
     * @return Function to apply two Applicative's values to a function
     */
    public static <W extends WitnessType,T,T2,R> BiFunction<AnyM<W,T>,AnyM<W,T2>,AnyM<W,R>> ap2(AnyM<W, Function<T,Function<T2,R>>> fn){
        return (apply1,apply2)->apply1.adapter().ap2(fn,apply1,apply2);
    }

    /**
     * Perform a filter operation on the wrapped monad instance e.g.
     * 
     * <pre>
     * {@code
     *   AnyM.fromOptional(Optional.of(10)).filter(i->i<10);
     * 
     *   //AnyM[Optional.empty()]
     *   
     *   AnyM.fromStream(Stream.of(5,10)).filter(i->i<10);
     *   
     *   //AnyM[Stream[5]]
     * }
     * 
     * 
     * </pre>
     * 
     * @param p Filtering predicate
     * @return Filtered AnyM
     */
    default  AnyM<W,T> filter(Predicate<? super T> fn){
        return adapter().filter(this, fn);
    }
   /**
    1. remove filterable
    2. create filterableAnyM subclass with filter operations
    3. remove AnyMValue / AnyMseq ?
    4. Add combine iterable / zip
    5. traverse / sequence methods
    6. remove bind method
    
    Monad transformers
    1. 1 type only AnyM
    2. map / filter (if filterable) / flatMap / flatMapT / zip - combine / fold - reduce operations
       on nested data structures (reduce etc all via map)
    3.  **/
    default <R> AnyM<W,R> coflatMapA(final Function<? super AnyM<W,T>, R> mapper) {
        return unit(Lambda.λ(()->mapper.apply(this))).map(Supplier::get);
    }
    
    
    default AnyM<W,AnyM<W,T>> nestA() {
        return unit(this);
    }
    
    /* (non-Javadoc)
     * @see com.aol.cyclops.types.EmptyUnit#emptyUnit()
     */
    @Override
    default <T> Unit<T> emptyUnit(){
        return adapter().empty();
    }

    /**
     * Tests for equivalency between two AnyM types
     * 
     * <pre>
     * {@code
     *    boolean eqv = AnyM.fromOptional(Optional.of(1)).eqv(AnyM.fromStream(Stream.of(1)));
     *    //true
     *     boolean eqv = AnyM.fromOptional(Optional.of(1)).eqv(AnyM.fromStream(Stream.of(1,2)));
     *    //false
     * }
     * </pre>
     * 
     * @param t AnyM to check for equivalence with this AnyM
     * @return true if monads are equivalent
     */
    default boolean eqv(final AnyM<?,T> t) {
        return Predicates.eqvIterable(t)
                         .test(this);
    }

    /**
     * Allows structural matching on the value / seq nature of this AnyM.
     * If this AnyM can only store a single value an Xor.secondary with type AnyMValue is returned
     * If this AnyM can  store one or many values an Xor.primary with type AnyMSeq is returned
     * 
     * <pre>
     * {@code
     *    AnyM<String> monad;
     *    
     *    monad.matchable().visit(v->handleValue(v.get()),s->handleSequence(s.toList()));
     * }
     * </pre>
     * 
     * 
     * @return An Xor for pattern matching either an AnyMValue or AnyMSeq
     */
    Xor<AnyMValue<W,T>, AnyMSeq<W,T>> matchable();



    /**
     * Collect the contents of the monad wrapped by this AnyM into supplied collector
     * A mutable reduction operation equivalent to Stream#collect
     * 
     * <pre>
     * {@code 
     *      AnyM<Integer> monad1 = AnyM.fromStream(Stream.of(1,2,3));
     *      AnyM<Integer> monad2 = AnyM.fromOptional(Optional.of(1));
     *      
     *      List<Integer> list1 = monad1.collect(Collectors.toList());
     *      List<Integer> list2 = monad2.collect(Collectors.toList());
     *      
     * }
     * </pre>
     * 
     * 
     * @param collector JDK collector to perform mutable reduction
     * @return Reduced value
     */
    default <R, A> R collect(Collector<? super T, A, R> collector){
        return stream().collect(collector);   
    }

    /* 
     * Convert this AnyM to an extended Stream (ReactiveSeq)
     * 
     * <pre>
     * {@code 
     *    AnyM<Integer> monad =  AnyM.fromOptional(Optional.of(10));
     *    
     *    Stream<Integer> stream = monad.stream();
     *    //ReactiveSeq[10]
     * }
     * </pre>
     * 
     */
    @Override
    default ReactiveSeq<T> stream(){
        return ReactiveSeq.fromIterable(this);
    }

    

    /**
     * Perform a peek operation on the wrapped monad e.g.
     * 
     * <pre>
     * {@code 
     *   AnyM.fromCompletableFuture(CompletableFuture.supplyAsync(()->loadData())
     *       .peek(System.out::println)
     * }
     * </pre>
     * 
     * @param c Consumer to accept current data
     * @return AnyM after peek operation
     */
    @Override
    default AnyM<W,T> peek(Consumer<? super T> c){
        return (AnyM<W, T>) Functor.super.peek(c);
    }



    /**
     * join / flatten one level of a nested hierarchy
     * 
     * @return Flattened / joined one level
     */ 
    static <W extends WitnessType,T1> AnyM<W,T1> flatten(AnyM<W,AnyM<W,T1>> nested){
        return nested.flatMapA(Function.identity());
    }

   
    /**
     * Aggregate the contents of this Monad and the supplied Monad 
     * 
     * <pre>{@code 
     * 
     * AnyM.fromStream(Stream.of(1,2,3,4))
     * 							.aggregate(anyM(Optional.of(5)))
     * 
     * AnyM[Stream[List[1,2,3,4,5]]
     * 
     * List<Integer> result = AnyM.fromStream(Stream.of(1,2,3,4))
     * 							.aggregate(anyM(Optional.of(5)))
     * 							.toSequence()
     *                          .flatten()
     * 							.toList();
    	
    	assertThat(result,equalTo(Arrays.asList(1,2,3,4,5)));
    	}</pre>
     * 
     * @param next Monad to aggregate content with
     * @return Aggregated Monad
     */
    default AnyM<W,List<T>> aggregate(AnyM<W,T> next){
        return unit(Stream.concat(matchable().visit(value -> value.stream(), seq -> seq.stream()), next.matchable()
                                  .visit(value -> value.stream(),
                                         seq -> seq.stream()))
                    .collect(Collectors.toList()));
    }

   
    

    /**
     * Construct an AnyM wrapping a new empty instance of the wrapped type 
     * 
     * e.g.
     * <pre>
     * {@code 
     * Any<Integer> ints = AnyM.fromStream(Stream.of(1,2,3));
     * AnyM<Integer> empty=ints.empty();
     * }
     * </pre>
     * @return Empty AnyM
     */
    default <T> AnyM<W,T> empty(){
        return adapter().empty();
    }

    
    /**
     * @return String representation of this AnyM
     */
    @Override
    public String toString();

    /**
     * Construct an AnyM instance that wraps a range from start (inclusive) to end (exclusive) provided
     * 
     * The AnyM will contain a SequenceM over the spefied range
     * 
     * @param start Inclusive start of the range
     * @param end Exclusive end of the range
     * @return AnyM range
     */
    public static AnyMSeq<stream,Integer> fromRange(final int start, final int end) {

        return AnyM.fromStream(ReactiveSeq.range(start, end));
    }

    /**
     * Construct an AnyM instance that wraps a range from start (inclusive) to end (exclusive) provided
     * 
     * The AnyM will contain a SequenceM over the spefied range
     * 
     * @param start Inclusive start of the range
     * @param end Exclusive end of the range
     * @return AnyM range
     */
    public static AnyMSeq<stream,Long> fromRangeLong(final long start, final long end) {

        return AnyM.fromStream(ReactiveSeq.rangeLong(start, end));
    }

    /**
     * Wrap a Streamable inside an AnyM
     * 
     * @param streamable wrap
     * @return AnyMSeq generated from a ToStream type
     */
    public static <T> AnyMSeq<streamable,T> fromStreamable(final ToStream<T> streamable) {
        Objects.requireNonNull(streamable);
        
        return AnyMFactory.instance.seq(Streamable.fromIterable(streamable),Witness.streamable.INSTANCE);
    }

    /**
     * Create an AnyM from a List
     * 
     * This AnyM will convert the List to a Stream under the covers, but will rematerialize the Stream as List
     * if wrap() is called
     * 
     * 
     * @param list to wrap inside an AnyM
     * @return AnyM wrapping a list
     */
    public static <T> AnyMSeq<list,T> fromList(final List<T> list) {
        Objects.requireNonNull(list);
        return AnyMFactory.instance.seq(list,Witness.list.INSTANCE);
    }
    public static <W extends Witness.CollectionXWitness,T> AnyMSeq<W,T> fromCollectionX(final CollectionX<T> collection, W witness) {
        Objects.requireNonNull(collection);
        return AnyMFactory.instance.seq(collection,witness);
    }

    /**
     * Create an AnyM from a Set
     * 
     * This AnyM will convert the Set to a Stream under the covers, but will rematerialize the Stream as Set
     * if wrap() is called
     * 
     * 
     * @param set to wrap inside an AnyM
     * @return AnyM wrapping a Set
     */
    public static <T> AnyMSeq<set,T> fromSet(final Set<T> set) {
        Objects.requireNonNull(set);
        return AnyMFactory.instance.seq(set, Witness.set.INSTANCE);
    }

    /**
     * Create an AnyM wrapping a Stream of the supplied data
     * 
     * @param streamData values to populate a Stream
     * @return AnyMSeq wrapping a Stream that encompasses the supplied Array
     */
    public static <T> AnyMSeq<stream,T> fromArray(final T... streamData) {
        return AnyMFactory.instance.seq(Stream.of(streamData),Witness.stream.INSTANCE);
    }

    /**
     * Create an AnyM wrapping a Stream of the supplied data
     * 
     * Identical to fromArray, exists as it may appear functionally more obvious to users than fromArray (which fits the convention)
     * 
     * @param streamData values to populate a Stream
     * @return  AnyMSeq wrapping a Stream that encompasses the supplied Array
     */
    public static <T> AnyMSeq<stream,T> streamOf(final T... streamData) {
        return AnyMFactory.instance.seq(Stream.of(streamData),Witness.streamable.INSTANCE);
    }

    /**
     * Construct an AnyM that wraps a reactive-streams Publisher. If there is no registered Comprehender for the supplied Publisher, this method
     *  will attempt to convert the Publisher to a type that cyclops-react can understand.
     *  
     *  <pre>
     *  {@code 
     *       AnyMSeq<Integer> flux = AnyM.fromPublisher(Flux.just(10,20,30));
     *       
     *       //with cyclops-reactor
     *       //AnyM[Flux[Integer]]]
     *       
     *       //without cyclops-reactor
     *       //AnyM[ReactiveSeq[Integer]]]
     *  }
     *  </pre>
     *  It is generally safer to define a Comprehender and use a non-converting call to generate the wrapped AnyM
     *       (e.g. Reactor.Flux in cyclops-reactor for Pivotal Reactor Publishers)
     * 
     * @param publisher Publisher to wrap inside an AnyM
     * @return AnyMSeq that wraps a Publisher
     */
    public static <T> AnyMSeq<stream,T> fromPublisher(final Publisher<T> publisher) {
        return AnyMFactory.instance.seq(ReactiveSeq.fromPublisher(publisher),Witness.stream.INSTANCE);
    }

    /**
     * Create an AnyM instance that wraps a Stream
     * 
     * @param stream Stream to wrap
     * @return AnyM that wraps the provided Stream
     */
    public static <T> AnyMSeq<stream,T> fromStream(final Stream<T> stream) {
        Objects.requireNonNull(stream);
        return AnyMFactory.instance.seq(stream,Witness.stream.INSTANCE);
    }

    /**
     * Create an AnyM instance that wraps an IntStream
     * 
     * @param stream IntStream to wrap
     * @return AnyM that wraps the provided IntStream
     */
    public static AnyMSeq<stream,Integer> fromIntStream(final IntStream stream) {
        Objects.requireNonNull(stream);
        return AnyMFactory.instance.seq(stream.boxed(),Witness.stream.INSTANCE);
    }

    /**
     * Create an AnyM instance that wraps an DoubleStream
     * 
     * @param stream DoubleStream to wrap
     * @return AnyM that wraps the provided DoubleStream
     */
    public static AnyMSeq<stream,Double> fromDoubleStream(final DoubleStream stream) {
        Objects.requireNonNull(stream);
        return AnyMFactory.instance.seq(stream.boxed(),Witness.stream.INSTANCE);
    }

    /**
     * Create an AnyM instance that wraps an LongStream
     * 
     * @param stream LongStream to wrap
     * @return AnyM that wraps the provided LongStream
     */
    public static AnyMSeq<stream,Long> fromLongStream(final LongStream stream) {
        Objects.requireNonNull(stream);
        return AnyMFactory.instance.seq(stream.boxed(),Witness.stream.INSTANCE);
    }

    /**
     * Create an AnyM instance that wraps an Optional
     * 
     * @param optional Optional to wrap
     * @return AnyM that wraps the provided Optonal
     */
    public static <T> AnyMValue<optional,T> fromOptional(final Optional<T> opt) {
        Objects.requireNonNull(opt);
        return AnyMFactory.instance.value(opt, Witness.optional.INSTANCE);
    }

    /**
     * Create an AnyM instance that wraps an OptionalDouble
     * 
     * @param optional Optional to wrap
     * @return AnyM that wraps the provided OptonalDouble
     */
    public static AnyMValue<optional,Double> fromOptionalDouble(final OptionalDouble optional) {
        Objects.requireNonNull(optional);
        return AnyMFactory.instance.value(Optionals.optional(optional), Witness.optional.INSTANCE);
    }

    /**
     * Create an AnyM instance that wraps an OptionalLong
     * 
     * @param optional OptionalLong to wrap
     * @return AnyM that wraps the provided OptonalLong
     */
    public static AnyMValue<optional,Long> fromOptionalLong(final OptionalLong optional) {
        Objects.requireNonNull(optional);
        return AnyMFactory.instance.value(Optionals.optional(optional), Witness.optional.INSTANCE);
    }

    /**
     * Create an AnyM instance that wraps an OptionalInt
     * 
     * @param optional OptionalInt to wrap
     * @return AnyM that wraps the provided OptonalInt
     */
    public static AnyMValue<optional,Integer> fromOptionalInt(final OptionalInt optional) {
        Objects.requireNonNull(optional);
        return AnyMFactory.instance.value(Optionals.optional(optional), Witness.optional.INSTANCE);
    }

    /**
     * Create an AnyM instance that wraps a CompletableFuture
     * 
     * @param future CompletableFuture to wrap
     * @return AnyM that wraps the provided CompletableFuture
     */
    public static <T> AnyMValue<completableFuture,T> fromCompletableFuture(final CompletableFuture<T> future) {
        Objects.requireNonNull(future);
        return AnyMFactory.instance.value(future, Witness.completableFuture.INSTANCE);
    }

    /**
     * Create an AnyMValue instance that wraps an Xor
     * 
     * @param xor Xor to wrap inside an AnyM
     * @return AnyM instance that wraps the provided Xor
     */
    public static <T> AnyMValue<xor,T> fromXor(final Xor<?, T> xor) {
        Objects.requireNonNull(xor);
        return AnyMFactory.instance.value(xor,Witness.xor.INSTANCE);
    }


    /**
     * Create an AnyMValue instance that wraps a Try
     * 
     * @param trySomething to wrap inside an AnyM
     * @return AnyM instance that wraps the provided Try
     */
    public static <T> AnyMValue<tryType,T> fromTry(final Try<T, ?> trySomething) {
        Objects.requireNonNull(trySomething);
        return AnyMFactory.instance.value(trySomething, Witness.tryType.INSTANCE);
    }

    /**
     *  Create an AnyMValue instance that wraps an Ior
     * 
     * @param ior to wrap inside an AnyM
     * @return AnyM instance that wraps the provided Ior
     */
    public static <T> AnyMValue<ior,T> fromIor(final Ior<?, T> ior) {
        Objects.requireNonNull(ior);
        return AnyMFactory.instance.value(ior, Witness.ior.INSTANCE);
    }

    /**
     * Create an AnyMValue instance that wraps an Eval
     * 
     * @param eval to wrap inside an AnyM
     * @return AnyM instance that wraps the provided Eval
     */
    public static <T> AnyMValue<eval,T> fromEval(final Eval<T> eval) {
        Objects.requireNonNull(eval);
        return AnyMFactory.instance.value(eval, Witness.eval.INSTANCE);
    }
    public static <W extends Witness.MonadicValueWitness,T> AnyMValue<W,T> fromMonadicValue(final MonadicValue<T> eval,W witness) {
        Objects.requireNonNull(eval);
        return AnyMFactory.instance.value(eval, witness);
    }

    /**
     * Create an AnyMValue instance that wraps a FutureW
     * 
     * @param future to wrap inside an AnyM
     * @return AnyM instance that wraps the provided future
     */
    public static <T> AnyMValue<futureW,T> fromFutureW(final FutureW<T> future) {
        Objects.requireNonNull(future);
        return AnyMFactory.instance.value(future, Witness.futureW.INSTANCE);
    }

    /**
     * Create an AnyMValue instance that wraps a {@link Maybe}
     * 
     * @param maybe to wrap inside an AnyM
     * @return instance that wraps the provided Maybe
     */
    public static <T> AnyMValue<maybe,T> fromMaybe(final Maybe<T> maybe) {
        Objects.requireNonNull(maybe);
        return AnyMFactory.instance.value(maybe, Witness.maybe.INSTANCE);
    }

    /**
     * Create an AnyMValue instance that wraps an EvalTransformer {@link EvalTValue}
     * 
     * @param evalT  to wrap inside an AnyM
     * @return instance that wraps the provided EvalTransformer
    
    public static <T> AnyMValue<eval,T> fromEvalTValue(final EvalTValue<T> evalT) {
        Objects.requireNonNull(evalT);
        return AnyMFactory.instance.value(evalT, Witness.eval.INSTANCE);
    }
 */
    /**
     *  Create an AnyMValue instance that wraps an MaybeTransformer {@link MaybeTValue}
     * 
     * @param maybeT  to wrap inside an AnyM
     * @return instance that wraps the provided MaybeTransformer
     
    public static <T> AnyMValue<T> fromMaybeTValue(final MaybeTValue<T> maybeT) {
        Objects.requireNonNull(maybeT);
        return AnyMFactory.instance.value(maybeT,new MaybeTValueComprehender());
    }*/

    /**
     * Create an AnyMValue instance that wraps an OptionalTransformer {@link OptionalTValue}
     * 
     * @param optionalT to wrap inside an AnyM
     * @return instance that wraps the provided OptionalTransformer
    
    public static <T> AnyMValue<T> fromOptionalTValue(final OptionalTValue<T> optionalT) {
        Objects.requireNonNull(optionalT);
        return AnyMFactory.instance.value(optionalT,new OptionalTValueComprehender());
    }
 */
    /**
     * Create an AnyMValue instance that wraps an CompletableFutureTransformer {@link CompletableFutureTValue}
     * 
     * @param futureT  to wrap inside an AnyM
     * @return instance that wraps the provided CompletableFutureTransformer
     
    public static <T> AnyMValue<T> fromCompletableFutureTValue(final CompletableFutureTValue<T> futureT) {
        Objects.requireNonNull(futureT);
        return AnyMFactory.instance.value(futureT,new CompletableFutureTValueComprehender());
    }*/

    /**
     *  Create an AnyMValue instance that wraps an XorTransformer {@link CompletableFutureTValue}
     * 
     * @param xorT to wrap inside an AnyM
     * @return instance that wraps the provided XorTransformer
    
    public static <ST, PT> AnyMValue<PT> fromXorTValue(final XorTValue<ST, PT> xorT) {
        Objects.requireNonNull(xorT);
        return AnyMFactory.instance.value(xorT,new XorTValueComprehender());
    } */

    /**
     * Create an AnyMValue instance that wraps an TryTransformer {@link TryTValue}
     * 
     * @param tryT to wrap inside an AnyM
     * @return instance that wraps the provided TryTransformer
    
    public static <T, X extends Throwable> AnyMValue<T> fromTryTValue(final TryTValue<T, X> tryT) {
        Objects.requireNonNull(tryT);
        return AnyMFactory.instance.value(tryT,new TryTValueComprehender());
    } */

    /**
     * Create an AnyMSeq instance that wraps an XorTransformer {@link XorTSeq}
     * 
     * @param xorT to wrap inside an AnyM
     * @return instance that wraps the provided XorTransformer
     
    public static <ST, PT> AnyMSeq<PT> fromXorTSeq(final XorTSeq<ST, PT> xorT) {
        Objects.requireNonNull(xorT);
        return AnyMFactory.instance.seq(xorT, new XorTSeqComprehender());
    }*/

    /**
     * Create an AnyMSeq instance that wraps an TryTransformer {@link TryTSeq}
     * 
     * @param tryT to wrap inside an AnyM
     * @return instance that wraps the provided TryTransformer
     
    public static <T, X extends Throwable> AnyMSeq<T> fromTryTSeq(final TryTSeq<T, X> tryT) {
        Objects.requireNonNull(tryT);
        return AnyMFactory.instance.seq(tryT,new TryTSeqComprehender());
    }*/

    /**
     * Create an AnyMSeq instance that wraps an EvalTransformer {@link EvalTSeq}
     * 
     * @param evalT to wrap inside an AnyM
     * @return instance that wraps the provided EvalTransformer
     
    public static <T> AnyMSeq<T> fromEvalTSeq(final EvalTSeq<T> evalT) {
        Objects.requireNonNull(evalT);
        return AnyMFactory.instance.seq(evalT,new EvalTSeqComprehender());
    }
*/
    /**
     * Create an AnyMSeq instance that wraps an MaybeTransformer {@link MaybeTSeq}
     * 
     * @param maybeT to wrap inside an AnyM
     * @return instance that wraps the provided MaybeTransformer
     
    public static <T> AnyMSeq<T> fromMaybeTSeq(final MaybeTSeq<T> maybeT) {
        Objects.requireNonNull(maybeT);
        return AnyMFactory.instance.seq(maybeT,new MaybeTSeqComprehender());
    }
*/
    /**
     * Create an AnyMSeq instance that wraps an OptionalTransformer {@link OptionalTSeq}
     * 
     * @param optionalT to wrap inside an AnyM
     * @return instance that wraps the provided OptionalTransformer
     
    public static <T> AnyMSeq<T> fromOptionalTSeq(final OptionalTSeq<T> optionalT) {
        Objects.requireNonNull(optionalT);
        return AnyMFactory.instance.seq(optionalT,new OptionalTSeqComprehender());
    }*/

    /**
     *  Create an AnyMSeq instance that wraps an CompletableFutureTransformer {@link CompletableFutureTSeq}
     * 
     * @param futureT to wrap inside an AnyM
     * @return instance that wraps the provided CompletableFutureTransformer
    
    public static <T> AnyMSeq<T> fromCompletableFutureTSeq(final CompletableFutureTSeq<T> futureT) {
        Objects.requireNonNull(futureT);
        return AnyMFactory.instance.seq(futureT,new CompletableFutureTSeqComprehender());
    } */

    /**
     * Create an AnyMValue instance that wraps an FutureWTransformer {@link FutureWTSeq}
     * 
     * @param futureT to wrap inside an AnyM
     * @return  instance that wraps the provided FutureWTransformer
     
    public static <T> AnyMValue<T> fromFutureWTValue(final FutureWTValue<T> futureT) {
        Objects.requireNonNull(futureT);
        return AnyMFactory.instance.value(futureT,new FutureWTSeqComprehender());
    }*/

    /**
     * Create an AnyMSeq instance that wraps an FutureWTransformer {@link FutureWTSeq}
     * 
     * @param futureT to wrap inside an AnyM
     * @return instance that wraps the provided FutureWTransformer
     
    public static <T> AnyMSeq<T> fromFutureWTSeq(final FutureWTSeq<T> futureT) {
        Objects.requireNonNull(futureT);
        return AnyMFactory.instance.seq(futureT);
    }*/

    /**
     * Create an AnyMSeq instance that wraps an ListTransformer {@link ListTSeq}
     * 
     * @param listT to wrap inside an AnyM
     * @return instance that wraps the provided ListTransformer
     
    public static <T> AnyMSeq<T> fromListT(final ListT<T> listT) {
        Objects.requireNonNull(listT);
        return AnyMFactory.instance.seq(listT,listT(listT).visit(v->new ListTValueComprehender(),
                                                                            s->new ListTSeqComprehender()));
    }*/

    /**
     * Create an AnyMSeq instance that wraps an StreamTransformer {@link StreamTSeq}
     * 
     * @param streamT to wrap inside an AnyM
     * @return instance that wraps the provided StreamTransformer
     
    public static <T> AnyMSeq<T> fromStreamT(final StreamT<T> streamT) {
        Objects.requireNonNull(streamT);
        return AnyMFactory.instance.seq(streamT,streamT(streamT).visit(v->new StreamTValueComprehender(),
                                                                 s->new StreamTSeqComprehender()));
       
    }*/

    /**
     * Create an AnyMSeq instance that wraps an StreamableTransformer {@link StreamableTSeq}
     * 
     * @param streamT  to wrap inside an AnyM
     * @return instance that wraps the provided StreamableTransformer
    
    public static <T> AnyMSeq<T> fromStreamableT(final StreamableT<T> streamT) {
        Objects.requireNonNull(streamT);
        return AnyMFactory.instance.seq(streamT,Matchables.streamableT(streamT).visit(v->new StreamableTValueComprehender(),
                                                                          s->new StreamableTSeqComprehender()));
    } */

    /**
     * Create an AnyMSeq instance that wraps an SetTransformer {@link SetTSeq}
     * 
     * @param setT to wrap inside an AnyM
     * @return instance that wraps the provided SetTransformer
     
    public static <T> AnyMSeq<T> fromSetT(final SetT<T> setT) {
        Objects.requireNonNull(setT);
        return AnyMFactory.instance.seq(setT,Matchables.setT(setT).visit(v->new SetTValueComprehender(),
                                                                       s->new SetTSeqComprehender()));
    }*/

    /**
     * Create an AnyM instance that wraps an Iterable
     * 
     * @param iterable Iterable to wrap
     * @return AnyM that wraps the provided Iterable
     
    public static <T> AnyMSeq<T> fromIterable(Iterable<T> iterable) {
        Objects.requireNonNull(iterable);
        if (iterable instanceof AnyMSeq)
            return (AnyMSeq<T>) iterable;
        if (iterable instanceof List)
            iterable = ListX.fromIterable(iterable);
        if (iterable instanceof Set)
            iterable = SetX.fromIterable(iterable);
        return AnyMFactory.instance.convertSeq(iterable);
    }*/

    /**
     * Use this method to create an AnyMValue from an Iterable.
     * This exists as many monadic value types in Java libraries implement iterable (such 
     * as Optional in Javaslang or FunctionalJava).
     * 
     * @param iterable To generate AnyMValue from
     * @return AnyMValue wrapping the supplied Iterable
    
    public static <T> AnyMValue<T> fromIterableValue(final Iterable<T> iterable) {
        Objects.requireNonNull(iterable);
        return AnyMFactory.instance.value(iterable);
    } */

    /**
     * Take the supplied object and attempt to convert it to a supported Monad type
     * 
     * @param monad Monad to convert to a supported type and wrap inside an AnyMValue
     * @return AnyMValue that wraps the supplied converted monad
     
    public static <T> AnyMValue<T> ofConvertableValue(final Object monad) {
        Objects.requireNonNull(monad);
        return AnyMFactory.instance.convertValue(monad);
    }*/

    /**
     * Take the supplied object and attempt to convert it to a supported Monad type
     * 
     * @param monad Monad to convert to a supported type and wrap inside an AnyMValue
     * @return AnyMSeq that wraps the supplied converted
    
    public static <T> AnyMSeq<T> ofConvertableSeq(final Object monad) {
        Objects.requireNonNull(monad);
        return AnyMFactory.instance.convertSeq(monad);
    } */

    /**
     * Create an AnyMValue that wraps the untyped monad
     * 
     * @param monad to wrap inside an AnyM
     * @return AnyMValue that wraps the supplied monad
    */
    public static <W extends WitnessType,T> AnyMValue<W,T> ofValue(final Object monad, W witness) {
        Objects.requireNonNull(monad);
        return AnyMFactory.instance.value(monad,witness);
    } 
    public static <W extends WitnessType,T> AnyMValue<W,T> ofValue(final Object monad,Comprehender<?> adapter) {
        Objects.requireNonNull(monad);
        return AnyMFactory.instance.value(monad,adapter);
    } 

    /**
     * Create an AnyMSeq that wraps the untyped monad
     * 
     * @param monad to wrap inside an AnyM
     * @return AnyMSeq that wraps the supplied monad
     */
    public static <W extends WitnessType,T> AnyMSeq<W,T> ofSeq(final Object monad, W witness) {
        Objects.requireNonNull(monad);
        return AnyMFactory.instance.seq(monad,witness);
    }

    /**
     * Generate an AnyM that wraps an Optional from the provided nullable object
     * 
     * @param nullable - Nullable object to generate an optional from
     * @return AnyM wrapping an Optional created with the supplied nullable
     */
    public static <T> AnyMValue<optional,T> ofNullable(final Object nullable) {
        return AnyMFactory.instance.value(Optional.ofNullable(nullable),Witness.optional.INSTANCE);
    }

    /**
     * Take an iterable containing Streamables and convert them into a List of AnyMs
     * e.g.
     * {@code 
     *     List<AnyM<Integer>> anyMs = AnyM.listFromStreamable(Arrays.asList(Streamable.of(1,2,3),Streamable.of(10,20,30));
     *     
     *     //List[AnyM[Streamable[1,2,3],Streamable[10,20,30]]]
     * }
     * 
     * @param anyM Iterable containing Streamables
     * @return List of AnyMs
     */
    public static <T> ListX<AnyMSeq<streamable,T>> listFromStreamable(final Iterable<Streamable<T>> anyM) {
        return StreamSupport.stream(anyM.spliterator(), false)
                            .map(i -> AnyM.fromStreamable(i))
                            .collect(ListX.listXCollector());
    }

    /**
     * Take an iterable containing Streams and convert them into a List of AnyMs
     * e.g.
     * {@code 
     *     List<AnyM<Integer>> anyMs = AnyM.listFromStream(Arrays.asList(Stream.of(1,2,3),Stream.of(10,20,30));
     *     
     *     //List[AnyM[Stream[1,2,3],Stream[10,20,30]]]
     * }
     * 
     * @param anyM Iterable containing Streams
     * @return List of AnyMs
     */
    public static <T> ListX<AnyMSeq<stream,T>> listFromStream(final Iterable<Stream<T>> anyM) {
        return StreamSupport.stream(anyM.spliterator(), false)
                            .map(i -> AnyM.fromStream(i))
                            .collect(ListX.listXCollector());
    }

    /**
     * Take an iterable containing Optionals and convert them into a List of AnyMs
     * e.g.
     * {@code 
     *     List<AnyM<Integer>> anyMs = AnyM.listFromStreamable(Arrays.asList(Optional.of(1),Optional.of(10));
     *     
     *     //List[AnyM[Optional[1],Optional[10]]]
     * }
     * 
     * @param anyM Iterable containing Optional
     * @return List of AnyMs
     */
    public static <T> ListX<AnyMValue<optional,T>> listFromOptional(final Iterable<Optional<T>> anyM) {
        return StreamSupport.stream(anyM.spliterator(), false)
                            .map(i -> AnyM.fromOptional(i))
                            .collect(ListX.listXCollector());
    }

    /**
     * Take an iterable containing CompletableFutures and convert them into a List of AnyMs
     * e.g.
     * {@code 
     *     List<AnyM<Integer>> anyMs = AnyM.listFromStreamable(Arrays.asList(CompletableFuture.completedFuture(1),CompletableFuture.supplyAsync(()->10));
     *     
     *     //List[AnyM[CompletableFuture[1],CompleteableFuture[10]]]
     * }
     * 
     * @param anyM Iterable containing CompletableFuture
     * @return List of AnyMs
     */
    public static <T> ListX<AnyMValue<completableFuture,T>> listFromCompletableFuture(final Iterable<CompletableFuture<T>> anyM) {
        return StreamSupport.stream(anyM.spliterator(), false)
                            .map(i -> AnyM.fromCompletableFuture(i))
                            .collect(ListX.listXCollector());
    }

    /**
     * Take an iterable containing Streamables and convert them into a List of AnyMs
     * e.g.
     * {@code 
     *     List<AnyM<Integer>> anyMs = AnyM.listFromStreamable(Arrays.asList(Streamable.of(1,2,3),Streamable.of(10,20,30));
     *     
     *     //List[AnyM[Streamable[1,2,3],Streamable[10,20,30]]]
     * }
     * 
     * @param anyM Iterable containing Streamables
     * @return List of AnyMs
     
    public static <T> ListX<AnyMSeq<T>> listFromIterable(final Iterable<Iterable<T>> anyM) {
        return StreamSupport.stream(anyM.spliterator(), false)
                            .map(i -> AnyM.fromIterable(i))
                            .collect(ListX.listXCollector());
    }
*/
    /**
     * Take an iterable containing Xors and convert them into a List of AnyMs
     * e.g.
     * {@code 
     *     List<AnyM<Integer>> anyMs = AnyM.listFromXor(Arrays.asList(Xor.primary(1),Xor.secondary(10));
     *     
     *     //List[AnyM[Xor:primary[1],Xor:secondaary[10]]]
     * }
     * 
     * @param anyM Iterable containing Xors
     * @return List of AnyMs
     */
    public static <ST, T> ListX<AnyMValue<xor,T>> listFromXor(final Iterable<Xor<ST, T>> anyM) {
        return StreamSupport.stream(anyM.spliterator(), false)
                            .map(i -> AnyM.fromXor(i))
                            .collect(ListX.listXCollector());
    }

    /**
     * Take an iterable containing Iors and convert them into a List of AnyMs
     * e.g.
     * {@code 
     *     List<AnyM<Integer>> anyMs = AnyM.listFromXor(Arrays.asList(Ior.primary(1),Ior.secondary(10));
     *     
     *     //List[AnyM[Ior:primary[1],Ior:secondaary[10]]]
     * }
     * 
     * @param anyM Iterable containing Iors
     * @return List of AnyMs
     */
    public static <ST, T> ListX<AnyMValue<ior,T>> listFromIor(final Iterable<Ior<ST, T>> anyM) {
        return StreamSupport.stream(anyM.spliterator(), false)
                            .map(i -> AnyM.fromIor(i))
                            .collect(ListX.listXCollector());
    }

    /**
     * Take an iterable containing Maybes and convert them into a List of AnyMs
     * e.g.
     * {@code 
     *     List<AnyM<Integer>> anyMs = AnyM.listFromXor(Arrays.asList(Maybe.just(1),Maybe.just(10));
     *     
     *     //List[AnyM[Maybe[1],Maybe[10]]]
     * }
     * 
     * @param anyM Iterable containing Maybes
     * @return List of AnyMs
     */
    public static <T> ListX<AnyMValue<maybe,T>> listFromMaybe(final Iterable<Maybe<T>> anyM) {
        return StreamSupport.stream(anyM.spliterator(), false)
                            .map(i -> AnyM.fromMaybe(i))
                            .collect(ListX.listXCollector());
    }

    /**
     * Take an iterable containing Evals and convert them into a List of AnyMs
     * e.g.
     * {@code 
     *     List<AnyM<Integer>> anyMs = AnyM.listFromXor(Arrays.asList(Eval.now(1),Eval.now(10));
     *     
     *     //List[AnyM[Eval[1],Eval[10]]]
     * }
     * 
     * @param anyM Iterable containing Maybes
     * @return List of AnyMs
     */
    public static <T> ListX<AnyMValue<eval,T>> listFromEval(final Iterable<Eval<T>> anyM) {
        return StreamSupport.stream(anyM.spliterator(), false)
                            .map(i -> AnyM.fromEval(i))
                            .collect(ListX.listXCollector());
    }

    /**
     * Take an iterable containing FutureW and convert them into a List of AnyMs
     * e.g.
     * {@code 
     *     List<AnyM<Integer>> anyMs = AnyM.listFromXor(Arrays.asList(FutureW.ofResult(1),FutureW.ofResult(10));
     *     
     *     //List[AnyM[FutureW[1],FutureW[10]]]
     * }
     * 
     * @param anyM Iterable containing Maybes
     * @return List of AnyMs
     */
    public static <T> ListX<AnyMValue<futureW,T>> listFromFutureW(final Iterable<FutureW<T>> anyM) {
        return StreamSupport.stream(anyM.spliterator(), false)
                            .map(i -> AnyM.fromFutureW(i))
                            .collect(ListX.listXCollector());
    }

    /**
     * Take an iterable containing Streamables and convert them into a List of AnyMs
     * e.g.
     * {@code 
     *     List<AnyM<Integer>> anyMs = AnyM.listFromStreamable(Arrays.asList(Arrays.asList(1,2,3).iterator(),Arrays.asList(10,20,30)).iterator();
     *     
     *     //List[AnyM[Stream[1,2,3],Stream[10,20,30]]]
     * }
     * 
     * @param anyM Iterable containing Iterators
     * @return List of AnyMs
     
    public static <T> ListX<AnyMSeq<T>> listFromIterator(final Iterable<Iterator<T>> anyM) {
        return StreamSupport.stream(anyM.spliterator(), false)
                            .map(i -> AnyM.fromIterable(() -> i))
                            .collect(ListX.listXCollector());
    }*/

    /**
     * Convert a Collection of Monads to a Monad with a List
     * 
     * <pre>
     * {@code
        List<CompletableFuture<Integer>> futures = createFutures();
        AnyM<List<Integer>> futureList = AnyMonads.sequence(AsAnyMList.anyMList(futures));
    
       //where AnyM wraps  CompletableFuture<List<Integer>>
      }</pre>
     * 
     * 
     * @param seq Collection of monads to convert
     * @return Monad with a List
     */
    public static <W extends WitnessType,T1> AnyM<W,ListX<T1>> sequence(final Collection<AnyM<W,T1>> seq,W w) {
        return sequence(seq.stream(),w).map(ListX::fromStreamS);
    }

    /**
     * Convert a Collection of Monads to a Monad with a List applying the supplied function in the process
     * 
     * <pre>
     * {@code 
       List<CompletableFuture<Integer>> futures = createFutures();
       AnyM<List<String>> futureList = AnyMonads.traverse(AsAnyMList.anyMList(futures), (Integer i) -> "hello" +i);
        }
        </pre>
     * 
     * @param seq Collection of Monads
     * @param fn Function to apply 
     * @return Monad with a list
     */
    public static <W extends WitnessType,T, R> AnyM<W,ListX<R>> traverse(final Collection<? extends AnyM<W,T>> seq, final Function<? super T, ? extends R> fn,W w) {
        return sequence(seq,w).map(l->l.map(fn));
    }

    

    static class AnyMFactory {
        static AnyMFactory instance = new AnyMFactory();

        public <W extends WitnessType,T> AnyMValue<W,T> value(final Object o,Comprehender<?> adapter) {
            if (o instanceof AnyMValue)
                return (AnyMValue<W,T>) o;
            
            return new AnyMValueImpl<W,T>(
                                        o,(Comprehender)adapter);
        }

        /**
         * Non-type safe way to wrap a supported monad type in an AnyMValue
         * 
         * @param o Monad to wrap
         * @return AnyMValue wrapping supplied monad
         */
        public <W extends WitnessType,T> AnyMValue<W,T> value(final Object o,WitnessType comp) {
            if (o instanceof AnyMValue)
                return (AnyMValue<W,T>) o;
            
            return new AnyMValueImpl<W,T>(
                                      o,comp.adapter());
        }

        /**
         * Non-type safe way to wrap a supported monad type in an AnyMSeq
         * 
         * @param o Monad to wrap
         * @return AnyMValue wrapping supplied monad
         */
        public <W extends WitnessType,T> AnyMSeq<W,T> seq(final Object o, WitnessType comp) {
            if (o instanceof AnyMSeq)
                return (AnyMSeq<W,T>) o;
            return new AnyMSeqImpl<W,T>(o,comp.adapter());
        }

    }
    public static  <W extends WitnessType,T> AnyM<W,Stream<T>> sequence(Stream<? extends AnyM<W,T>> stream, W witness) {
        Comprehender<W> c = witness.adapter();
        AnyM<W,Stream<T>> identity = c.unit(Stream.empty());
       
        BiFunction<AnyM<W,Stream<T>>,AnyM<W,T>,AnyM<W,Stream<T>>> combineToStream = (acc,next) -> c.ap2(c.unit(Lambda.l2((Stream<T> a)->(T b)->Stream.concat(a,Stream.of(b)))),acc,next);

        BinaryOperator<AnyM<W,Stream<T>>> combineStreams = (a,b)->a;//a.apply(b, (s1,s2)->s1);  

        return stream.reduce(identity,combineToStream,combineStreams);
    }
    public static  <W extends WitnessType,T,R> AnyM<W,Stream<R>> traverse(Function<T,R> fn,Stream<AnyM<W,T>> stream, W witness) {
       return sequence(stream.map(h->h.map(fn)),witness);
    }
    Comprehender<W> adapter();

}