package org.thoughtcrime.redphone.crypto;

import android.test.AndroidTestCase;

public class SequenceCounterTest extends AndroidTestCase{
	public void testTrivial() {
		SequenceCounter s = new SequenceCounter();
		assertEquals(0, s.convertNext((short)0));
		assertEquals(1, s.convertNext((short)1));
		assertEquals(-1, s.convertNext((short)-1));
	}
	public void testSignedOverflow() {
		SequenceCounter s = new SequenceCounter();
		assertEquals(Short.MAX_VALUE, s.convertNext(Short.MAX_VALUE));
		assertEquals(Short.MAX_VALUE + 1, s.convertNext(Short.MIN_VALUE));
	}
	public void testSignedUnderflow() {
		SequenceCounter s = new SequenceCounter();
		assertEquals(Short.MIN_VALUE, s.convertNext(Short.MIN_VALUE));
		assertEquals(Short.MIN_VALUE - 1, s.convertNext(Short.MAX_VALUE));
	}
	public void testUnsignedOverflow() {
		SequenceCounter s = new SequenceCounter();
		int maxUShort = (1 << 16) - 1;
		assertEquals(-1, s.convertNext((short)maxUShort));
		assertEquals(-2, s.convertNext((short)(maxUShort-1)));
		assertEquals(0, s.convertNext((short)0));
	}
	public void testIncreasing() {
		SequenceCounter s = new SequenceCounter();
		for (long id = 0; id < 1 << 17; id += 23) {
			assertEquals(id, s.convertNext((short)id));
		}
	}
	public void testDecreasing() {
		SequenceCounter s = new SequenceCounter();
		for (long id = 0; id > -(1 << 17); id -= 29) {
			assertEquals(id, s.convertNext((short)id));
		}
	}
	public void testPerturbed() {
		java.util.Random r = new java.util.Random(1234);
		SequenceCounter s = new SequenceCounter();
		long id = 0;
		for (int repeat = 0; repeat < 10000; repeat++) {
			short delta = (short)r.nextInt(1 << 16);
			id += delta;
			assertEquals(id, s.convertNext((short)id));
		}
	}
}
