/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.inventory.backend;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Iterator;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
final class FareySequence {

    public static Interval intervalForPath(Iterable<Integer> treePath) {
        RationalNumber low = new RationalNumber(1, 2);
        RationalNumber high = new RationalNumber(1, 1);

        Iterator<Integer> it = treePath.iterator();
        while (it.hasNext()) {
            long treeIdx = it.next();
            RationalNumber lastHigh = high;
            for (long ti = 0; ti < treeIdx; ++ti) {
                lastHigh = high;
                high = high.mediant(low);
            }

            low = high;
            high = lastHigh;
        }

        return new Interval(low, high);
    }

    public static final class Interval {
        private final RationalNumber low;
        private final RationalNumber high;

        public Interval(RationalNumber low, RationalNumber high) {
            this.low = low;
            this.high = high;
        }

        public RationalNumber getLow() {
            return low;
        }

        public RationalNumber getHigh() {
            return high;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Interval)) return false;

            Interval interval = (Interval) o;

            if (!low.equals(interval.low)) return false;
            return high.equals(interval.high);
        }

        @Override public int hashCode() {
            int result = low.hashCode();
            result = 31 * result + high.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "[" + low + ", " + high + "]";
        }
    }

    public static final class RationalNumber {
        final long numerator;
        final long denominator;

        public RationalNumber(long numerator, long denominator) {
            this.numerator = numerator;
            this.denominator = denominator;
        }

        public RationalNumber mediant(RationalNumber other) {
            if (Long.MAX_VALUE - this.numerator < other.numerator
                    || Long.MAX_VALUE - this.denominator < other.denominator) {

                throw new ArithmeticException("Cannot express the mediant of " + this + " and " + other + "." +
                        " The value would arithmetically overflow.");
            }
            return new RationalNumber(this.numerator + other.numerator, this.denominator + other.denominator);
        }

        public BigDecimal toDecimal() {
            return new BigDecimal(numerator).divide(new BigDecimal(denominator), MathContext.DECIMAL128);
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RationalNumber)) return false;

            RationalNumber that = (RationalNumber) o;

            if (numerator != that.numerator) return false;
            return denominator == that.denominator;
        }

        @Override public int hashCode() {
            int result = (int) (numerator ^ (numerator >>> 32));
            result = 31 * result + (int) (denominator ^ (denominator >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return numerator + "/" + denominator;
        }
    }
}
