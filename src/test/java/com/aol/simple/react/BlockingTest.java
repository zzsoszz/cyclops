package com.aol.simple.react;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.junit.Test;

import com.aol.simple.react.stream.SimpleReact;
import com.aol.simple.react.stream.api.FutureStream;

public class BlockingTest {

	
	
	@Test
	public void testBlockStreamsSeparateExecutors() throws InterruptedException,
			ExecutionException {

		Integer result = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 200)
				.block()
				.parallelStream()
				.filter(f -> f > 300)
				.map(m -> m - 5)
				.reduce(0, (acc, next) -> acc + next);

		assertThat(result, is(990));
	}

	@Test
	public void testTypeInferencingCapture(){
		List<String> result = new SimpleReact().react(() -> "World",()-> "Hello").then( in -> "hello")
				.capture(e -> e.printStackTrace()).block();
		assertThat(result.size(),is(2));
	
	}
	@Test
	public void testTypeInferencingThen(){
		List<String> result = new SimpleReact().react(() -> "World",()-> "Hello").then( in -> "hello")
				.block();
		assertThat(result.size(),is(2));
	
	}
	@Test
	public void testTypeInferencingThenPredicate(){
		List<String> result = new SimpleReact().react(() -> "World",()-> "Hello").then( in -> "hello")
				.block(state -> state.getCompleted()>3);
		assertThat(result.size(),is(2));
	
	}
	
	
	
	@Test
	public void testBlock() throws InterruptedException, ExecutionException {

		List<String> strings = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 100)
				.then(it -> "*" + it)
				.block();

		assertThat(strings.size(), is(3));

	}
	
	@Test
	public void testBlockToSet() throws InterruptedException, ExecutionException {

		Set<String> strings = new SimpleReact()
				.<Integer> react(() -> 1, () -> 1, () -> 3)
				.then(it -> it * 100)
				.then(it -> "*" + it)
				.block(Collectors.toSet());

		assertThat(strings.size(), is(2));

	}
	
	@Test
	public void testBreakout() throws InterruptedException, ExecutionException {
		Throwable[] error = { null };
		List<String> strings = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 100)
				.then(it -> {
					if (it == 100)
						throw new RuntimeException("boo!");

					return it;
				})
				.onFail(e -> 1)
				.then(it -> "*" + it)
				.block(status -> status.getCompleted() > 1);

		assertThat(strings.size(), is(2));

	}
	@Test
	public void testBreakoutToSet() throws InterruptedException, ExecutionException {
		Throwable[] error = { null };
		Set<String> strings = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 100)
				.then(it -> {
					if (it == 100)
						throw new RuntimeException("boo!");

					return it;
				})
				.onFail(e -> 1)
				.then(it -> "*" + it)
				.block(Collectors.toSet(),status -> status.getCompleted() > 1);

		assertThat(strings.size(), greaterThan(1));

	}

	@Test
	public void testBreakoutException() throws InterruptedException,
			ExecutionException {
		Throwable[] error = { null };
		List<Integer> results = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 100)
				.<Integer>then(it -> {

					throw new RuntimeException("boo!");
					

				}).capture(e -> error[0] = e)
				.block(status -> status.getCompleted() >= 1);

		assertThat(results.size(), is(0));
		assertThat(error[0], equalTo(RuntimeException.class));
	}
	volatile int count =0;
	@Test
	public void testBreakoutExceptionTimes() throws InterruptedException,
			ExecutionException {
		count =0;
		List<Integer> results = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 100)
				.<Integer>then(it -> {

					throw new RuntimeException("boo!");

				}).capture(e -> count++)
				.block(status -> status.getCompleted() >= 1);

		assertThat(results.size(), is(0));
		assertThat(count, is(3));
	}
	@Test
	public void testBreakoutAllCompleted() throws InterruptedException,
			ExecutionException {
		count =0;
		List<Integer> results = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 100)
				.then(it -> {
					if(it==100)
						throw new RuntimeException("boo!");
					else
						sleep(it);
					return it;

				}).capture(e -> count++)
				.block(status -> status.getAllCompleted() >0);

		assertThat(results.size(), is(0));
		assertThat(count, is(1));
	}
	@Test
	public void testBreakoutAllCompletedStrings() throws InterruptedException,
			ExecutionException {
		count =0;
		List<String> strings = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 100)
				.then(it -> {
					if(it==100)
						throw new RuntimeException("boo!");
					else
						sleep(it);
					return it;

				}).capture(e -> count++)
				.then( it -> "*" + it)
				.block(status -> status.getAllCompleted() >0);

		assertThat(strings.size(), is(0));
		assertThat(count, is(1));
	}
	@Test
	public void testBreakoutAllCompletedAndTime() throws InterruptedException,
			ExecutionException {
			count =0;
			List<Integer> result = new SimpleReact()
					.<Integer> react(() -> 1, () -> 2, () -> 3)
					.then(it -> it * 100)
					.then(it -> {
						sleep(it);
						return it;
	
					}).capture(e -> count++)
					.block(status -> status.getAllCompleted() >1 && status.getElapsedMillis()>200);
	
			assertThat(result.size(), is(2));
			assertThat(count, is(0));
	}
	

	@Test
	public void testBreakoutInEffective() throws InterruptedException,
			ExecutionException {
		Throwable[] error = { null };
		List<String> strings = new SimpleReact()
				.<Integer> react(() -> 1, () -> 2, () -> 3)
				.then(it -> it * 100)
				.then(it -> {
					if (it == 100)
						throw new RuntimeException("boo!");

					return it;
				}).onFail(e -> 1)
				.then(it -> "*" + it)
				.block(status -> status.getCompleted() > 5);

		assertThat(strings.size(), is(3));

	}
	@Test
	public void testLast() throws InterruptedException, ExecutionException {

		Integer result = new SimpleReact()
		.<Integer> react(() -> 1, () -> 2, () -> 3, () -> 5)
		.then( it -> it*100)
		.then( it -> sleep(it))
		.last();

		assertThat(result,is(500));
	}
	@Test
	public void testFirstSimple() throws InterruptedException, ExecutionException {

		FutureStream<Integer> stage = new SimpleReact()
		.<Integer> react(() -> 1, () -> 2, () -> 3, () -> 5)
		.then( it -> it*100)
		.then( it -> sleep(it));
		
		int result = stage.first();

		assertThat(result,is(100));
		
		stage.block();
	}
	 
	

	@Test
	public void testFirstAllOf() throws InterruptedException, ExecutionException {

		Set<Integer> result = new SimpleReact()
		.<Integer> react(() -> 1, () -> 2, () -> 3, () -> 5)
		.then( it -> it*100)
		.<Set<Integer>,Set<Integer>>allOf(Collectors.toSet(), it -> {
			assertThat (it,is( Set.class));
			return it;
		}).first();

		assertThat(result.size(),is(4));
	}
	@Test
	public void testLastAllOf() throws InterruptedException, ExecutionException {

		Set<Integer> result = new SimpleReact()
		.<Integer> react(() -> 1, () -> 2, () -> 3, () -> 5)
		.then( it -> it*100)
		.<Set<Integer>,Set<Integer>>allOf(Collectors.toSet(), it -> {
			assertThat (it,is( Set.class));
			return it;
		}).last();

		assertThat(result.size(),is(4));
	}
	
	private Integer sleep(Integer it) {
		try {
			Thread.sleep(it);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return it;
	}
}
