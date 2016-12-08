package com.aol.cyclops.control.monads.transformers.seq;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.jooq.lambda.Collectable;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.jooq.lambda.tuple.Tuple4;
import org.reactivestreams.Publisher;

import com.aol.cyclops.Monoid;
import com.aol.cyclops.control.AnyM;
import com.aol.cyclops.control.FutureW;
import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.control.monads.transformers.FutureWT;
import com.aol.cyclops.control.monads.transformers.values.ValueTransformerSeq;
import com.aol.cyclops.data.collections.extensions.standard.ListX;
import com.aol.cyclops.types.Filterable;
import com.aol.cyclops.types.Functor;
import com.aol.cyclops.types.IterableFoldable;
import com.aol.cyclops.types.MonadicValue;
import com.aol.cyclops.types.Sequential;
import com.aol.cyclops.types.To;
import com.aol.cyclops.types.Traversable;
import com.aol.cyclops.types.Unit;
import com.aol.cyclops.types.anyM.AnyMSeq;
import com.aol.cyclops.types.anyM.Witness;
import com.aol.cyclops.types.anyM.WitnessType;
import com.aol.cyclops.types.stream.ConvertableSequence;
import com.aol.cyclops.types.stream.CyclopsCollectable;
import com.aol.cyclops.types.stream.ToStream;
import com.aol.cyclops.util.function.Lambda;

/**
* Monad Transformer for FutureW's nested within Sequential or non-scalar data types (e.g. Lists, Streams etc)

 * 
 * FutureWT allows the deeply wrapped FutureW to be manipulating within it's nested /contained context
 *
 * @author johnmcclean
 *
 * @param <A> Type of data stored inside the nested FutureW(s)
 */
public class FutureWTSeq<W extends WitnessType,A>
        implements To<FutureWTSeq<W,A>>,Unit<A>, Publisher<A>, Functor<A>, Filterable<A>, ToStream<A>, ValueTransformerSeq<W,A>, IterableFoldable<A>, ConvertableSequence<A>, CyclopsCollectable<A>, Sequential<A> {

    private final AnyM<W,FutureW<A>> run;

    /**
     * @return The wrapped AnyM
     */
    @Override
    public AnyM<W,FutureW<A>> unwrap() {
        return run;
    }

    private FutureWTSeq(final AnyM<W,FutureW<A>> run) {
        this.run = run;
    }

    @Override
    public <T> FutureWTSeq<W,T> unitStream(final ReactiveSeq<T> traversable) {
        return FutureWT.fromStream(traversable.map(FutureW::ofResult));

    }

    @Override
    public <T> FutureWTSeq<W,T> unitAnyM(final AnyM<W,Traversable<T>> traversable) {

        return of((AnyMSeq) traversable.map(t -> FutureW.fromIterable(t)));
    }

    @Override
    public AnyM<W,? extends Traversable<A>> transformerStream() {

        return run.map(f -> f.toListX());
    }

    @Override
    public FutureWTSeq<W,A> filter(final Predicate<? super A> test) {
        return of(run.map(f->f.map(in->Tuple.tuple(in,test.test(in))))
                     .filter( f->f.get().v2 )
                     .map( f->f.map(in->in.v1)));
    }

    /**
     * Peek at the current value of the FutureW
     * <pre>
     * {@code 
     *    FutureWT.of(AnyM.fromStream(Arrays.asFutureW(10))
     *             .peek(System.out::println);
     *             
     *     //prints 10        
     * }
     * </pre>
     * 
     * @param peek  Consumer to accept current value of FutureW
     * @return FutureWT with peek call
     */
    @Override
    public FutureWTSeq<W,A> peek(final Consumer<? super A> peek) {
        return of(run.peek(future -> future.map(a -> {
            peek.accept(a);
            return a;
        })));
    }

    /**
     * Map the wrapped FutureW
     * 
     * <pre>
     * {@code 
     *  FutureWT.of(AnyM.fromStream(Arrays.asFutureW(10))
     *             .map(t->t=t+1);
     *  
     *  
     *  //FutureWT<AnyMSeq<Stream<FutureW[11]>>>
     * }
     * </pre>
     * 
     * @param f Mapping function for the wrapped FutureW
     * @return FutureWT that applies the map function to the wrapped FutureW
     */
    @Override
    public <B> FutureWTSeq<W,B> map(final Function<? super A, ? extends B> f) {
        return new FutureWTSeq<W,B>(
                                  run.map(o -> o.map(f)));
    }

    /**
     * Flat Map the wrapped FutureW
      * <pre>
     * {@code 
     *  FutureWT.of(AnyM.fromStream(Arrays.asFutureW(10))
     *             .flatMap(t->FutureW.completedFuture(20));
     *  
     *  
     *  //FutureWT<AnyMSeq<Stream<FutureW[20]>>>
     * }
     * </pre>
     * @param f FlatMap function
     * @return FutureWT that applies the flatMap function to the wrapped FutureW
     */

    public <B> FutureWTSeq<W,B> flatMapT(final Function<? super A, FutureWTSeq<W,B>> f) {
        return of(run.map(future -> future.flatMap(a -> f.apply(a).run.stream()
                                                                      .toList()
                                                                      .get(0))));
    }

    private static <W extends WitnessType,B> AnyM<W,FutureW<B>> narrow(final AnyM<W,FutureW<? extends B>> run) {
        return (AnyM) run;
    }

    @Override
    public <B> FutureWTSeq<W,B> flatMap(final Function<? super A, ? extends MonadicValue<? extends B>> f) {

        final AnyM<W,FutureW<? extends B>> mapped = run.map(o -> o.flatMap(f));
        return of(narrow(mapped));

    }

    /**
     * Lift a function into one that accepts and returns an FutureWT
     * This allows multiple monad types to add functionality to existing functions and methods
     * 
     * e.g. to add list handling  / iteration (via FutureW) and iteration (via Stream) to an existing function
     * <pre>
     * {@code 
        Function<Integer,Integer> add2 = i -> i+2;
    	Function<FutureWT<Integer>, FutureWT<Integer>> optTAdd2 = FutureWT.lift(add2);
    	
    	Stream<Integer> withNulls = Stream.of(1,2,3);
    	AnyMSeq<Integer> stream = AnyM.fromStream(withNulls);
    	AnyMSeq<FutureW<Integer>> streamOpt = stream.map(FutureW::completedFuture);
    	List<Integer> results = optTAdd2.apply(FutureWT.of(streamOpt))
    									.unwrap()
    									.<Stream<FutureW<Integer>>>unwrap()
    									.map(FutureW::join)
    									.collect(Collectors.toList());
    	
    	
    	//FutureW.completedFuture(List[3,4]);
     * 
     * 
     * }</pre>
     * 
     * 
     * @param fn Function to enhance with functionality from FutureW and another monad type
     * @return Function that accepts and returns an FutureWT
     */
    public static <W extends WitnessType,U, R> Function<FutureWTSeq<W,U>, FutureWTSeq<W,R>> lift(final Function<? super U, ? extends R> fn) {
        return optTu -> optTu.map(input -> fn.apply(input));
    }

    /**
     * Lift a BiFunction into one that accepts and returns  FutureWTs
     * This allows multiple monad types to add functionality to existing functions and methods
     * 
     * e.g. to add list handling / iteration (via FutureW), iteration (via Stream)  and asynchronous execution (FutureW) 
     * to an existing function
     * 
     * <pre>
     * {@code 
    	BiFunction<Integer,Integer,Integer> add = (a,b) -> a+b;
    	BiFunction<FutureWT<Integer>,FutureWT<Integer>,FutureWT<Integer>> optTAdd2 = FutureWT.lift2(add);
    	
    	Stream<Integer> withNulls = Stream.of(1,2,3);
    	AnyMSeq<Integer> stream = AnyM.ofMonad(withNulls);
    	AnyMSeq<FutureW<Integer>> streamOpt = stream.map(FutureW::completedFuture);
    	
    	FutureW<FutureW<Integer>> two = FutureW.completedFuture(FutureW.completedFuture(2));
    	AnyMSeq<FutureW<Integer>> future=  AnyM.fromFutureW(two);
    	List<Integer> results = optTAdd2.apply(FutureWT.of(streamOpt),FutureWT.of(future))
    									.unwrap()
    									.<Stream<FutureW<Integer>>>unwrap()
    									.map(FutureW::join)
    									.collect(Collectors.toList());
    									
    		//FutureW.completedFuture(List[3,4,5]);						
      }
      </pre>
     * @param fn BiFunction to enhance with functionality from FutureW and another monad type
     * @return Function that accepts and returns an FutureWT
     */
    public static <W extends WitnessType,U1, U2, R> BiFunction<FutureWTSeq<W,U1>, FutureWTSeq<W,U2>, FutureWTSeq<W,R>> lift2(
            final BiFunction<? super U1, ? super U2, ? extends R> fn) {
        return (optTu1, optTu2) -> optTu1.flatMapT(input1 -> optTu2.map(input2 -> fn.apply(input1, input2)));
    }

    /**
     * Construct an FutureWT from an AnyM that contains a monad type that contains type other than FutureW
     * The values in the underlying monad will be mapped to FutureW<A>
     * 
     * @param anyM AnyM that doesn't contain a monad wrapping an FutureW
     * @return FutureWT
     */
    public static <W extends WitnessType,A> FutureWTSeq<W,A> fromAnyM(final AnyM<W,A> anyM) {
        return of(anyM.map(FutureW::ofResult));
    }

    /**
     * Construct an FutureWT from an AnyM that wraps a monad containing  FutureWs
     * 
     * @param monads AnyM that contains a monad wrapping an FutureW
     * @return FutureWT
     */
    public static <W extends WitnessType,A> FutureWTSeq<W,A> of(final AnyM<W,FutureW<A>> monads) {
        return new FutureWTSeq<>(
                                 monads);
    }
/**
    public static <W extends WitnessType,A> FutureWTSeq<W,A> of(final FutureW<A> monads) {
        return FutureWT.fromIterable(ListX.of(monads));
    }
**/
    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return String.format("FutureTSeq[%s]", run);
    }

    @Override
    public ReactiveSeq<A> stream() {
        return run.stream()
                  .map(cf -> cf.get());
    }

    @Override
    public Iterator<A> iterator() {
        return stream().iterator();
    }

    public <R> FutureWTSeq<W,R> unitIterator(final Iterator<R> it) {
        return of(run.unitIterator(it)
                     .map(i -> FutureW.ofResult(i)));
    }

    @Override
    public <R> FutureWTSeq<W,R> unit(final R value) {
        return of(run.unit(FutureW.ofResult(value)));
    }

    @Override
    public <R> FutureWTSeq<W,R> empty() {
        return of(run.unit(FutureW.empty()));
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.stream.CyclopsCollectable#collectable()
     */
    @Override
    public Collectable<A> collectable() {
        return stream();
    }

    @Override
    public boolean isSeqPresent() {
        return !run.isEmpty();
    }

    

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#combine(java.util.function.BiPredicate, java.util.function.BinaryOperator)
     */
    @Override
    public FutureWTSeq<W,A> combine(final BiPredicate<? super A, ? super A> predicate, final BinaryOperator<A> op) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.combine(predicate, op);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#cycle(int)
     */
    @Override
    public FutureWTSeq<W,A> cycle(final int times) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.cycle(times);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#cycle(com.aol.cyclops.Monoid, int)
     */
    @Override
    public FutureWTSeq<W,A> cycle(final Monoid<A> m, final int times) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.cycle(m, times);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#cycleWhile(java.util.function.Predicate)
     */
    @Override
    public FutureWTSeq<W,A> cycleWhile(final Predicate<? super A> predicate) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.cycleWhile(predicate);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#cycleUntil(java.util.function.Predicate)
     */
    @Override
    public FutureWTSeq<W,A> cycleUntil(final Predicate<? super A> predicate) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.cycleUntil(predicate);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#zip(java.lang.Iterable, java.util.function.BiFunction)
     */
    @Override
    public <U, R> FutureWTSeq<W,R> zip(final Iterable<? extends U> other, final BiFunction<? super A, ? super U, ? extends R> zipper) {

        return (FutureWTSeq<W,R>) ValueTransformerSeq.super.zip(other, zipper);
    }

    @Override
    public <U, R> FutureWTSeq<W,R> zip(final Stream<? extends U> other, final BiFunction<? super A, ? super U, ? extends R> zipper) {

        return (FutureWTSeq<W,R>) ValueTransformerSeq.super.zip(other, zipper);
    }

    @Override
    public <U, R> FutureWTSeq<W,R> zip(final Seq<? extends U> other, final BiFunction<? super A, ? super U, ? extends R> zipper) {

        return (FutureWTSeq<W,R>) ValueTransformerSeq.super.zip(other, zipper);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#zip(java.util.stream.Stream)
     */
    @Override
    public <U> FutureWTSeq<W,Tuple2<A, U>> zip(final Stream<? extends U> other) {

        return (FutureWTSeq) ValueTransformerSeq.super.zip(other);
    }

    @Override
    public <U> FutureWTSeq<W,Tuple2<A, U>> zip(final Iterable<? extends U> other) {

        return (FutureWTSeq) ValueTransformerSeq.super.zip(other);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#zip(org.jooq.lambda.Seq)
     */
    @Override
    public <U> FutureWTSeq<W,Tuple2<A, U>> zip(final Seq<? extends U> other) {

        return (FutureWTSeq) ValueTransformerSeq.super.zip(other);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#zip3(java.util.stream.Stream, java.util.stream.Stream)
     */
    @Override
    public <S, U> FutureWTSeq<W,Tuple3<A, S, U>> zip3(final Stream<? extends S> second, final Stream<? extends U> third) {

        return (FutureWTSeq) ValueTransformerSeq.super.zip3(second, third);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#zip4(java.util.stream.Stream, java.util.stream.Stream, java.util.stream.Stream)
     */
    @Override
    public <T2, T3, T4> FutureWTSeq<W,Tuple4<A, T2, T3, T4>> zip4(final Stream<? extends T2> second, final Stream<? extends T3> third,
            final Stream<? extends T4> fourth) {

        return (FutureWTSeq) ValueTransformerSeq.super.zip4(second, third, fourth);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#zipWithIndex()
     */
    @Override
    public FutureWTSeq<W,Tuple2<A, Long>> zipWithIndex() {

        return (FutureWTSeq<W,Tuple2<A, Long>>) ValueTransformerSeq.super.zipWithIndex();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#sliding(int)
     */
    @Override
    public FutureWTSeq<W,ListX<A>> sliding(final int windowSize) {

        return (FutureWTSeq<W,ListX<A>>) ValueTransformerSeq.super.sliding(windowSize);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#sliding(int, int)
     */
    @Override
    public FutureWTSeq<W,ListX<A>> sliding(final int windowSize, final int increment) {

        return (FutureWTSeq<W,ListX<A>>) ValueTransformerSeq.super.sliding(windowSize, increment);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#grouped(int, java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super A>> FutureWTSeq<W,C> grouped(final int size, final Supplier<C> supplier) {

        return (FutureWTSeq<W,C>) ValueTransformerSeq.super.grouped(size, supplier);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#groupedUntil(java.util.function.Predicate)
     */
    @Override
    public FutureWTSeq<W,ListX<A>> groupedUntil(final Predicate<? super A> predicate) {

        return (FutureWTSeq<W,ListX<A>>) ValueTransformerSeq.super.groupedUntil(predicate);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#groupedStatefullyUntil(java.util.function.BiPredicate)
     */
    @Override
    public FutureWTSeq<W,ListX<A>> groupedStatefullyUntil(final BiPredicate<ListX<? super A>, ? super A> predicate) {

        return (FutureWTSeq<W,ListX<A>>) ValueTransformerSeq.super.groupedStatefullyUntil(predicate);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#groupedWhile(java.util.function.Predicate)
     */
    @Override
    public FutureWTSeq<W,ListX<A>> groupedWhile(final Predicate<? super A> predicate) {

        return (FutureWTSeq<W,ListX<A>>) ValueTransformerSeq.super.groupedWhile(predicate);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#groupedWhile(java.util.function.Predicate, java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super A>> FutureWTSeq<W,C> groupedWhile(final Predicate<? super A> predicate, final Supplier<C> factory) {

        return (FutureWTSeq<W,C>) ValueTransformerSeq.super.groupedWhile(predicate, factory);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#groupedUntil(java.util.function.Predicate, java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super A>> FutureWTSeq<W,C> groupedUntil(final Predicate<? super A> predicate, final Supplier<C> factory) {

        return (FutureWTSeq<W,C>) ValueTransformerSeq.super.groupedUntil(predicate, factory);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#grouped(int)
     */
    @Override
    public FutureWTSeq<W,ListX<A>> grouped(final int groupSize) {

        return (FutureWTSeq<W,ListX<A>>) ValueTransformerSeq.super.grouped(groupSize);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#grouped(java.util.function.Function, java.util.stream.Collector)
     */
    @Override
    public <K, T, D> FutureWTSeq<W,Tuple2<K, D>> grouped(final Function<? super A, ? extends K> classifier,
            final Collector<? super A, T, D> downstream) {

        return (FutureWTSeq) ValueTransformerSeq.super.grouped(classifier, downstream);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#grouped(java.util.function.Function)
     */
    @Override
    public <K> FutureWTSeq<W,Tuple2<K, Seq<A>>> grouped(final Function<? super A, ? extends K> classifier) {

        return (FutureWTSeq) ValueTransformerSeq.super.grouped(classifier);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#distinct()
     */
    @Override
    public FutureWTSeq<W,A> distinct() {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.distinct();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#scanLeft(com.aol.cyclops.Monoid)
     */
    @Override
    public FutureWTSeq<W,A> scanLeft(final Monoid<A> monoid) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.scanLeft(monoid);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#scanLeft(java.lang.Object, java.util.function.BiFunction)
     */
    @Override
    public <U> FutureWTSeq<W,U> scanLeft(final U seed, final BiFunction<? super U, ? super A, ? extends U> function) {

        return (FutureWTSeq<W,U>) ValueTransformerSeq.super.scanLeft(seed, function);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#scanRight(com.aol.cyclops.Monoid)
     */
    @Override
    public FutureWTSeq<W,A> scanRight(final Monoid<A> monoid) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.scanRight(monoid);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#scanRight(java.lang.Object, java.util.function.BiFunction)
     */
    @Override
    public <U> FutureWTSeq<W,U> scanRight(final U identity, final BiFunction<? super A, ? super U, ? extends U> combiner) {

        return (FutureWTSeq<W,U>) ValueTransformerSeq.super.scanRight(identity, combiner);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#sorted()
     */
    @Override
    public FutureWTSeq<W,A> sorted() {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.sorted();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#sorted(java.util.Comparator)
     */
    @Override
    public FutureWTSeq<W,A> sorted(final Comparator<? super A> c) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.sorted(c);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#takeWhile(java.util.function.Predicate)
     */
    @Override
    public FutureWTSeq<W,A> takeWhile(final Predicate<? super A> p) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.takeWhile(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#dropWhile(java.util.function.Predicate)
     */
    @Override
    public FutureWTSeq<W,A> dropWhile(final Predicate<? super A> p) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.dropWhile(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#takeUntil(java.util.function.Predicate)
     */
    @Override
    public FutureWTSeq<W,A> takeUntil(final Predicate<? super A> p) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.takeUntil(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#dropUntil(java.util.function.Predicate)
     */
    @Override
    public FutureWTSeq<W,A> dropUntil(final Predicate<? super A> p) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.dropUntil(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#dropRight(int)
     */
    @Override
    public FutureWTSeq<W,A> dropRight(final int num) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.dropRight(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#takeRight(int)
     */
    @Override
    public FutureWTSeq<W,A> takeRight(final int num) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.takeRight(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#skip(long)
     */
    @Override
    public FutureWTSeq<W,A> skip(final long num) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.skip(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#skipWhile(java.util.function.Predicate)
     */
    @Override
    public FutureWTSeq<W,A> skipWhile(final Predicate<? super A> p) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.skipWhile(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#skipUntil(java.util.function.Predicate)
     */
    @Override
    public FutureWTSeq<W,A> skipUntil(final Predicate<? super A> p) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.skipUntil(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#limit(long)
     */
    @Override
    public FutureWTSeq<W,A> limit(final long num) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.limit(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#limitWhile(java.util.function.Predicate)
     */
    @Override
    public FutureWTSeq<W,A> limitWhile(final Predicate<? super A> p) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.limitWhile(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#limitUntil(java.util.function.Predicate)
     */
    @Override
    public FutureWTSeq<W,A> limitUntil(final Predicate<? super A> p) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.limitUntil(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#intersperse(java.lang.Object)
     */
    @Override
    public FutureWTSeq<W,A> intersperse(final A value) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.intersperse(value);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#reverse()
     */
    @Override
    public FutureWTSeq<W,A> reverse() {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.reverse();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#shuffle()
     */
    @Override
    public FutureWTSeq<W,A> shuffle() {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.shuffle();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#skipLast(int)
     */
    @Override
    public FutureWTSeq<W,A> skipLast(final int num) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.skipLast(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#limitLast(int)
     */
    @Override
    public FutureWTSeq<W,A> limitLast(final int num) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.limitLast(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#onEmpty(java.lang.Object)
     */
    @Override
    public FutureWTSeq<W,A> onEmpty(final A value) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.onEmpty(value);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#onEmptyGet(java.util.function.Supplier)
     */
    @Override
    public FutureWTSeq<W,A> onEmptyGet(final Supplier<? extends A> supplier) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.onEmptyGet(supplier);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#onEmptyThrow(java.util.function.Supplier)
     */
    @Override
    public <X extends Throwable> FutureWTSeq<W,A> onEmptyThrow(final Supplier<? extends X> supplier) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.onEmptyThrow(supplier);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#shuffle(java.util.Random)
     */
    @Override
    public FutureWTSeq<W,A> shuffle(final Random random) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.shuffle(random);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#slice(long, long)
     */
    @Override
    public FutureWTSeq<W,A> slice(final long from, final long to) {

        return (FutureWTSeq<W,A>) ValueTransformerSeq.super.slice(from, to);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#sorted(java.util.function.Function)
     */
    @Override
    public <U extends Comparable<? super U>> FutureWTSeq<W,A> sorted(final Function<? super A, ? extends U> function) {
        return (FutureWTSeq) ValueTransformerSeq.super.sorted(function);
    }

    @Override
    public int hashCode() {
        return run.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof FutureWTSeq) {
            return run.equals(((FutureWTSeq) o).run);
        }
        return false;
    }

}