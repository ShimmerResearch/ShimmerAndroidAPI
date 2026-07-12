package pl.flex_it.androidplot;

import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import com.androidplot.series.XYSeries;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeriesFormatter;
import com.androidplot.xy.XYSeriesRenderer;



/**
 * 
 * This example is based on:
 * - multitouch example, by David Buezas (david.buezas at gmail.com) and Michael 
 * 	from http://androidplot.com/wiki/A_Simple_XYPlot_with_multi-touch_zooming_and_scrolling
 * - AndroidPlot quickstart example http://androidplot.com/wiki/Quickstart
 * No license was given with this samples, but I assume that use it for free on BSD-like license ;-)
 * 
 * @author Marcin Lepicki (marcin.lepicki at flex-it.pl)
 *
 */
public class MultitouchPlot extends XYPlot implements OnTouchListener
{

	// Definition of the touch states
	static final private int NONE = 0;
	static final private int ONE_FINGER_DRAG = 1;
	static final private int TWO_FINGERS_DRAG = 2;
	private int mode = NONE;

	private Number minXSeriesValue;
	private Number maxXSeriesValue;
	private Number minYSeriesValue;
	private Number maxYSeriesValue;

	private PointF firstFinger;
	private float lastScrolling;
	private float distBetweenFingers;

	private Number newMinX;
	private Number newMaxX;

	public MultitouchPlot(Context context, String title)
	{
		super(context, title);
		initTouchHandling();
	}

	public MultitouchPlot(Context context, AttributeSet attributes)
	{
		super(context, attributes);
		initTouchHandling();
	}

	public MultitouchPlot(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		initTouchHandling();
	}

	@Override
	protected XYSeriesRenderer a(Class aClass) {
		return null;
	}

	private void initTouchHandling()
	{
		this.setOnTouchListener(this);
	}


	public boolean addSeries(XYSeries series, XYSeriesFormatter formatter)
	{
		//Overriden to compute min and max series values
		for(int i = 0; i < series.size(); i++)
		{
			if(minXSeriesValue == null ||  minXSeriesValue.doubleValue() > series.getX(i).doubleValue())
				minXSeriesValue = series.getX(i);
			if(maxXSeriesValue == null || maxXSeriesValue.doubleValue() < series.getX(i).doubleValue())
				maxXSeriesValue = series.getX(i);

			if(minYSeriesValue == null || minYSeriesValue.doubleValue() > series.getY(i).doubleValue())
				minYSeriesValue = series.getY(i);
			if(maxYSeriesValue == null || maxYSeriesValue.doubleValue() < series.getY(i).doubleValue())
				maxYSeriesValue = series.getY(i);
		}
		return super.addSeries(series, formatter);
	}


	public boolean onTouch(View view, MotionEvent motionEvent)
	{

		switch(motionEvent.getAction() & MotionEvent.ACTION_MASK)
		{
			case MotionEvent.ACTION_DOWN: //start gesture
				firstFinger = new PointF(motionEvent.getX(), motionEvent.getY());
				mode = ONE_FINGER_DRAG;
				break;

			case MotionEvent.ACTION_POINTER_DOWN: //second finger
			{
				if (motionEvent.getPointerCount() >= 2) {
					distBetweenFingers = distance(motionEvent);
					// the distance check is done to avoid false alarms
					if (distBetweenFingers > 5f || distBetweenFingers < -5f)
						mode = TWO_FINGERS_DRAG;
				}
				break;
			}

			case MotionEvent.ACTION_POINTER_UP: //end zoom
				//should I count pointers and change mode after only one is left?

				mode = ONE_FINGER_DRAG;

				break;

			case MotionEvent.ACTION_MOVE:
				if(mode == ONE_FINGER_DRAG)
				{
					calculateMinMaxVals();

					final PointF oldFirstFinger = firstFinger;
					firstFinger = new PointF(motionEvent.getX(), motionEvent.getY());
					lastScrolling = oldFirstFinger.x - firstFinger.x;
					scroll(lastScrolling);
					fixBoundariesForScroll();

					setDomainBoundaries(newMinX, newMaxX, BoundaryMode.FIXED);
					redraw();
				}
				else if(mode == TWO_FINGERS_DRAG)
				{
					if (motionEvent.getPointerCount() < 2)
					{
						break;
					}

					calculateMinMaxVals();

					final float oldDist = distBetweenFingers;
					final float newDist = distance(motionEvent);
					if(oldDist > 0 && newDist < 0 || oldDist < 0 && newDist > 0) //sign change! Fingers have crossed ;-)
						break;

					distBetweenFingers = newDist;

					zoom(oldDist / distBetweenFingers);

					fixBoundariesForZoom();
					setDomainBoundaries(newMinX, newMaxX, BoundaryMode.FIXED);
					redraw();
				}
				break;
		}

		return true;
	}

	private void scroll(float pan)
	{
		float calculatedMinX = getCalculatedMinX().floatValue();
		float calculatedMaxX = getCalculatedMaxX().floatValue();
		final float domainSpan =  calculatedMaxX - calculatedMinX;
		final float step = domainSpan / getWidth();
		final float offset = pan * step;

		newMinX = calculatedMinX + offset;
		newMaxX = calculatedMaxX + offset;
	}

	private void fixBoundariesForScroll()
	{
		float diff = newMaxX.floatValue() - newMinX.floatValue();
		if(newMinX.floatValue() < minXSeriesValue.floatValue())
		{
			newMinX = minXSeriesValue;
			newMaxX = newMinX.floatValue() + diff;
		}
		if(newMaxX.floatValue() > maxXSeriesValue.floatValue())
		{
			newMaxX = maxXSeriesValue;
			newMinX = newMaxX.floatValue() - diff;
		}
	}

	private float distance(MotionEvent event)
	{
		final float x = event.getX(0) - event.getX(1);
		return x;
	}

	private void zoom(float scale)
	{
		if(Float.isInfinite(scale) || Float.isNaN(scale) || (scale > -0.001 && scale < 0.001)) //sanity check
			return;

		float calculatedMinX = getCalculatedMinX().floatValue();
		float calculatedMaxX = getCalculatedMaxX().floatValue();
		final float domainSpan =  calculatedMaxX - calculatedMinX;
		final float domainMidPoint = calculatedMaxX - domainSpan / 2.0f;
		final float offset = domainSpan * scale / 2.0f;
		newMinX = domainMidPoint - offset;
		newMaxX = domainMidPoint + offset;
	}

	private void fixBoundariesForZoom()
	{
		if(newMinX.floatValue() < minXSeriesValue.floatValue())
		{
			newMinX = minXSeriesValue;
		}
		if(newMaxX.floatValue() > maxXSeriesValue.floatValue())
		{
			newMaxX = maxXSeriesValue;
		}
	}
}