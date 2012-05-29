/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spatial4j.core.shape;

import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;
import com.spatial4j.core.context.SpatialContext;
import com.spatial4j.core.distance.DistanceCalculator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.spatial4j.core.shape.SpatialRelation.*;


public abstract class AbstractTestShapes extends RandomizedTest {

  protected SpatialContext ctx;
  private static final double EPS = 10e-9;

  public AbstractTestShapes(SpatialContext ctx) {
    this.ctx = ctx;
  }

  protected void assertRelation(String msg, SpatialRelation expected, Shape a, Shape b) {
    msg = a+" intersect "+b;//use different msg
    _assertIntersect(msg,expected,a,b);
    //check flipped a & b w/ transpose(), while we're at it
    _assertIntersect("(transposed) " + msg, expected.transpose(), b, a);
  }

  private void _assertIntersect(String msg, SpatialRelation expected, Shape a, Shape b) {
    SpatialRelation sect = a.relate(b, ctx);
    if (sect == expected)
      return;
    if (expected == WITHIN || expected == CONTAINS) {
      if (a.getClass().equals(b.getClass())) // they are the same shape type
        assertEquals(msg,a,b);
      else {
        //they are effectively points or lines that are the same location
        assertTrue(msg,!a.hasArea());
        assertTrue(msg,!b.hasArea());

        Rectangle aBBox = a.getBoundingBox();
        Rectangle bBBox = b.getBoundingBox();
        if (aBBox.getHeight() == 0 && bBBox.getHeight() == 0
            && (aBBox.getMaxY() == 90 && bBBox.getMaxY() == 90
          || aBBox.getMinY() == -90 && bBBox.getMinY() == -90))
          ;//== a point at the pole
        else
          assertEquals(msg, aBBox, bBBox);
      }
    } else {
      assertEquals(msg,expected,sect);
    }
  }

  private void assertEqualsRatio(String msg, double expected, double actual) {
    double delta = Math.abs(actual - expected);
    double base = Math.min(actual, expected);
    double deltaRatio = base==0 ? delta : Math.min(delta,delta / base);
    assertEquals(msg,0,deltaRatio, EPS);
  }

  protected void testRectangle(double minX, double width, double minY, double height) {
    Rectangle r = ctx.makeRect(minX, minX + width, minY, minY+height);
    //test equals & hashcode of duplicate
    Rectangle r2 = ctx.makeRect(minX, minX + width, minY, minY+height);
    assertEquals(r,r2);
    assertEquals(r.hashCode(),r2.hashCode());

    String msg = r.toString();

    assertEquals(msg, width != 0 && height != 0, r.hasArea());
    assertEquals(msg, width != 0 && height != 0, r.getArea() > 0);

    assertEqualsRatio(msg, height, r.getHeight());
    assertEqualsRatio(msg, width, r.getWidth());
    Point center = r.getCenter();
    msg += " ctr:"+center;
    //System.out.println(msg);
    assertRelation(msg, CONTAINS, r, center);

    DistanceCalculator dc = ctx.getDistCalc();
    double dUR = dc.distance(center, r.getMaxX(), r.getMaxY());
    double dLR = dc.distance(center, r.getMaxX(), r.getMinY());
    double dUL = dc.distance(center, r.getMinX(), r.getMaxY());
    double dLL = dc.distance(center, r.getMinX(), r.getMinY());

    assertEquals(msg,width != 0 || height != 0, dUR != 0);
    if (dUR != 0)
      assertTrue(dUR > 0 && dLL > 0);
    assertEqualsRatio(msg, dUR, dUL);
    assertEqualsRatio(msg, dLR, dLL);
    if (!ctx.isGeo() || center.getY() == 0)
      assertEqualsRatio(msg, dUR, dLL);
  }

  protected void testRectIntersect() {
    final double INCR = 45;
    final double Y = 20;
    for(double left = -180; left <= 180; left += INCR) {
      for(double right = left; right - left <= 360; right += INCR) {
        Rectangle r = ctx.makeRect(left,right,-Y,Y);

        //test contains (which also tests within)
        for(double left2 = left; left2 <= right; left2 += INCR) {
          for(double right2 = left2; right2 <= right; right2 += INCR) {
            Rectangle r2 = ctx.makeRect(left2,right2,-Y,Y);
            assertRelation(null, SpatialRelation.CONTAINS, r, r2);

            //test point contains
            assertRelation(null, SpatialRelation.CONTAINS, r, r2.getCenter());
          }
        }

        //test disjoint
        for(double left2 = right+INCR; left2 - left < 360; left2 += INCR) {
          //test point disjoint
          assertRelation(null, SpatialRelation.DISJOINT, r, ctx.makePoint(left2, randomIntBetween(-90,90)));

          for(double right2 = left2; right2 - left < 360; right2 += INCR) {
            Rectangle r2 = ctx.makeRect(left2,right2,-Y,Y);
            assertRelation(null, SpatialRelation.DISJOINT, r, r2);
          }
        }
        //test intersect
        for(double left2 = left+INCR; left2 <= right; left2 += INCR) {
          for(double right2 = right+INCR; right2 - left < 360; right2 += INCR) {
            Rectangle r2 = ctx.makeRect(left2,right2,-Y,Y);
            assertRelation(null, SpatialRelation.INTERSECTS, r, r2);
          }
        }

      }
    }
  }

  protected void testCircle(double x, double y, double dist) {
    Circle c = ctx.makeCircle(x, y, dist);
    String msg = c.toString();
    final Circle c2 = ctx.makeCircle(ctx.makePoint(x, y), dist);
    assertEquals(c, c2);
    assertEquals(c.hashCode(),c2.hashCode());

    assertEquals(msg,dist > 0, c.hasArea());
    final Rectangle bbox = c.getBoundingBox();
    assertEquals(msg,dist > 0, bbox.getArea() > 0);
    if (!ctx.isGeo()) {
      //if not geo then units of dist == units of x,y
      assertEqualsRatio(msg, bbox.getHeight(), dist * 2);
      assertEqualsRatio(msg, bbox.getWidth(), dist * 2);
    }
    assertRelation(msg, CONTAINS, c, c.getCenter());
    assertRelation(msg, CONTAINS, bbox, c);
  }

  protected void testCircleIntersect() {
    //Now do some randomized tests:
    int i_C = 0, i_I = 0, i_W = 0, i_O = 0;//counters for the different intersection cases
    int laps = 0;
    int MINLAPSPERCASE = 20 * (int)multiplier();
    while(i_C < MINLAPSPERCASE || i_I < MINLAPSPERCASE || i_W < MINLAPSPERCASE || i_O < MINLAPSPERCASE) {
      laps++;
      final int TEST_DIVISIBLE = 2;//just use even numbers in this test
      double cX = randomIntBetweenDivisible(-180, 179, TEST_DIVISIBLE);
      double cY = randomIntBetweenDivisible(-90, 90, TEST_DIVISIBLE);
      double cR = randomIntBetweenDivisible(0, 180, TEST_DIVISIBLE);
      double cR_dist = ctx.getDistCalc().distance(ctx.makePoint(0, 0), 0, cR);
      Circle c = ctx.makeCircle(cX, cY, cR_dist);

      Rectangle r = randomRectangle(TEST_DIVISIBLE);

      SpatialRelation ic = c.relate(r, ctx);

      Point p;
      switch (ic) {
        case CONTAINS:
          i_C++;
          p = randomPointWithin(r);
          assertEquals(CONTAINS,c.relate(p, ctx));
          break;
        case INTERSECTS:
          i_I++;
          //hard to test anything here; instead we'll test it separately
          break;
        case WITHIN:
          i_W++;
          p = randomPointWithin(c);
          assertEquals(CONTAINS,r.relate(p, ctx));
          break;
        case DISJOINT:
          i_O++;
          p = randomPointWithin(r);
          assertEquals(DISJOINT,c.relate(p, ctx));
          break;
        default: fail(""+ic);
      }
    }
    //System.out.println("Laps: "+laps);

    //TODO deliberately test INTERSECTS based on known intersection point
  }

  private Rectangle randomRectangle(int divisible) {
    double rX = randomIntBetweenDivisible(-180, 180, divisible);
    double rW = randomIntBetweenDivisible(0, 360, divisible);
    double rY1 = randomIntBetweenDivisible(-90, 90, divisible);
    double rY2 = randomIntBetweenDivisible(-90, 90, divisible);
    double rYmin = Math.min(rY1,rY2);
    double rYmax = Math.max(rY1,rY2);
    return ctx.makeRect(rX, rX+rW, rYmin, rYmax);
  }

  @Test
  @Repeat(iterations = 20)
  public void testMultiShape() {
    assumeFalse(ctx.isGeo());//TODO not yet supported!

    //come up with some random shapes
    int NUM_SHAPES = randomIntBetween(1, 5);
    List<Rectangle> shapes = new ArrayList<Rectangle>(NUM_SHAPES);
    while (shapes.size() < NUM_SHAPES) {
      shapes.add(randomRectangle(20));
    }
    MultiShape multiShape = new MultiShape(shapes,ctx);

    //test multiShape.getBoundingBox();
    Rectangle msBbox = multiShape.getBoundingBox();
    if (shapes.size() == 1) {
      assertEquals(shapes.get(0),msBbox.getBoundingBox());
    } else {
      for (Rectangle shape : shapes) {
        assertRelation("bbox contains shape",CONTAINS, msBbox, shape);
      }
    }

    //TODO test multiShape.relate()

  }

  /** Returns a random integer between [start, end]. Integers between must be divisible by the 3rd argument. */
  private int randomIntBetweenDivisible(int start, int end, int divisible) {
    // DWS: I tested this
    int divisStart = (int) Math.ceil( (start+1) / (double)divisible );
    int divisEnd = (int) Math.floor( (end-1) / (double)divisible );
    int divisRange = Math.max(0,divisEnd - divisStart + 1);
    int r = randomInt(1 + divisRange);//remember that '0' is counted
    if (r == 0)
      return start;
    if (r == 1)
      return end;
    return (r-2 + divisStart)*divisible;
  }

  private Point randomPointWithin(Circle c) {
    double d = c.getDistance() * randomDouble();
    double angleDEG = 360 * randomDouble();
    Point p = ctx.getDistCalc().pointOnBearing(c.getCenter(), d, angleDEG, ctx);
    assertEquals(CONTAINS,c.relate(p, ctx));
    return p;
  }

  private Point randomPointWithin(Rectangle r) {
    double x = r.getMinX() + randomDouble()*r.getWidth();
    double y = r.getMinY() + randomDouble()*r.getHeight();
    Point p = ctx.makePoint(x,y);
    assertEquals(CONTAINS,r.relate(p, ctx));
    return p;
  }

}
