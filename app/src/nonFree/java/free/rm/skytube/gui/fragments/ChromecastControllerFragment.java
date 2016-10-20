package free.rm.skytube.gui.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import butterknife.ButterKnife;
import free.rm.skytube.R;
import free.rm.skytube.gui.businessobjects.FragmentEx;

public class ChromecastControllerFragment extends FragmentEx {
	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_chromecast_controller, container);
		ButterKnife.bind(this, view);
		return view;
	}
}
