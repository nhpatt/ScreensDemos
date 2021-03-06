package com.nhpatt.dremelapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import com.liferay.mobile.android.callback.typed.GenericCallback;
import com.liferay.mobile.android.service.Session;
import com.liferay.mobile.android.v62.dlapp.DLAppService;
import com.liferay.mobile.screens.asset.AssetEntry;
import com.liferay.mobile.screens.asset.list.AssetListScreenlet;
import com.liferay.mobile.screens.asset.AssetFactory;
import com.liferay.mobile.screens.base.list.BaseListListener;
import com.liferay.mobile.screens.context.SessionContext;
import com.liferay.mobile.screens.util.JSONUtil;
import com.liferay.mobile.screens.util.LiferayLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.json.JSONArray;
import org.json.JSONObject;

public class FinderActivity extends AppCompatActivity implements BaseListListener<AssetEntry> {

	private AssetListScreenlet assetListScreenlet;
	private Stack<AssetEntry> stack = new Stack<>();
	private AssetEntry oldAssetEntry = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_finder);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		assetListScreenlet = (AssetListScreenlet) findViewById(R.id.tasks);
		assetListScreenlet.setListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (!stack.isEmpty()) {
			searchForAssets(oldAssetEntry);
		} else {
			final AssetEntry assetEntry = getIntent().getParcelableExtra("ASSET");
			searchForAssets(assetEntry);
		}
	}

	protected void searchForAssets(final AssetEntry assetEntry) {
		if (assetEntry == null) {
			assetListScreenlet.setPortletItemName("tools");
			assetListScreenlet.loadPage(0);
		} else {
			new Thread(new Runnable() {
				@Override
				public void run() {
					doInBackground(assetEntry);
				}
			}).start();
		}
	}

	private void doInBackground(AssetEntry assetEntry) {
		try {
			Map<String, Object> values = assetEntry.getValues();
			int groupId = (int) values.get("groupId");
			int classPK = (int) (values.containsKey("classPK") ? values.get("classPK") : values.get("folderId"));

			DLAppService dlAppService = new DLAppService(SessionContext.createSessionFromCurrentSession());
			JSONArray folders = dlAppService.getFolders(groupId, classPK);

			final List<AssetEntry> assets = new ArrayList<>();
			for (int i = 0; i < folders.length(); i++) {
				JSONObject jsonObject = folders.getJSONObject(i);
				assets.add(AssetFactory.createInstance(JSONUtil.toMap(jsonObject)));
			}

			if (assets.isEmpty()) {
				JSONArray files = dlAppService.getFileEntries(groupId, classPK);

				for (int i = 0; i < files.length(); i++) {
					JSONObject jsonObject = files.getJSONObject(i);
					assets.add(AssetFactory.createInstance(JSONUtil.toMap(jsonObject)));
				}
			}

			doOnUiThread(assets);
		} catch (Exception e) {
			LiferayLogger.e(e.getMessage(), e);
		}
	}

	private void doOnUiThread(final List<AssetEntry> assets) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				assetListScreenlet.onListRowsReceived(0, assets.size(), assets, assets.size());
			}
		});
	}

	@Override
	public void onListPageFailed(int startRow, Exception e) {

	}

	@Override
	public void onListPageReceived(int startRow, int endRow, List<AssetEntry> entries, int rowCount) {

	}

	@Override
	public void onListItemSelected(AssetEntry assetEntry, View view) {

		if (assetEntry.getValues().containsKey("extension")) {
			try {
				Session session = SessionContext.createSessionFromCurrentSession();
				session.setCallback(new GenericCallback<JSONObject>() {
					@Override
					public void onFailure(Exception exception) {

					}

					@Override
					public void onSuccess(JSONObject result) {
						Intent intent = new Intent(FinderActivity.this, AccessoriesActivity.class);
						intent.putExtra("fileEntry", String.valueOf(result));
						startActivity(intent);
					}

					@Override
					public JSONObject transform(Object obj) throws Exception {
						return (JSONObject) obj;
					}
				});
				FileEntryService fileEntryService = new FileEntryService(session);
				fileEntryService.getFileEntry(Long.valueOf((Integer) assetEntry.getValues().get("fileEntryId")));
			} catch (Exception e) {
				LiferayLogger.e("Error", e);
			}
		} else {
			stack.push(oldAssetEntry);
			this.oldAssetEntry = assetEntry;
			searchForAssets(assetEntry);
		}
	}

	@Override
	public void onBackPressed() {
		if (stack.isEmpty()) {
			super.onBackPressed();
		} else {
			oldAssetEntry = stack.pop();
			searchForAssets(oldAssetEntry);
		}
	}

	@Override
	public void error(Exception e, String userAction) {

	}
}
