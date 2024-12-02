package com.example.map_north;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener;
import com.baidu.mapapi.search.poi.PoiCitySearchOption;
import com.baidu.mapapi.search.poi.PoiDetailResult;
import com.baidu.mapapi.search.poi.PoiDetailSearchResult;
import com.baidu.mapapi.search.poi.PoiIndoorResult;
import com.baidu.mapapi.search.poi.PoiResult;
import com.baidu.mapapi.search.poi.PoiSearch;
import com.baidu.mapapi.search.route.BikingRouteResult;
import com.baidu.mapapi.search.route.DrivingRouteLine;
import com.baidu.mapapi.search.route.DrivingRouteResult;
import com.baidu.mapapi.search.route.IndoorRouteResult;
import com.baidu.mapapi.search.route.MassTransitRouteResult;
import com.baidu.mapapi.search.route.OnGetRoutePlanResultListener;
import com.baidu.mapapi.search.route.RoutePlanSearch;
import com.baidu.mapapi.search.route.TransitRouteResult;
import com.baidu.mapapi.search.route.WalkingRouteResult;
import com.baidu.navisdk.adapter.BNRoutePlanNode;
import com.baidu.navisdk.adapter.BaiduNaviManagerFactory;
import com.baidu.navisdk.adapter.IBNRoutePlanManager;
import com.baidu.navisdk.adapter.IBNTTSManager;
import com.baidu.navisdk.adapter.IBaiduNaviManager;
import com.baidu.navisdk.adapter.struct.BNTTsInitConfig;
import com.baidu.navisdk.adapter.struct.BNaviInitConfig;


import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import android.Manifest;
import android.content.pm.PackageManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity{
    private MapView mMapView = null;
    private LocationClient  mLocationClient=null;
    private BDLocation mCurLocation=null;
    private EditText mInputText=null;
    private File mSDCardPath=null;
    private static final String APP_FOLDER_NAME = "map_north";
    private PoiInfo mDestation=null;

    private PoiSearch mPoiSearch=null;

    //权限数组
    private final String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    //请求权限意图
    private ActivityResultLauncher<String[]> requestPermissionIntent;

    class MyLocationListener extends BDAbstractLocationListener {
        private boolean isFirstLoc = true;
        private boolean autoLocation = false;

        public void setAutoLocation(boolean b) {
            autoLocation = b;
        }

        @Override
        public void onReceiveLocation(BDLocation bdLocation) {
            //mapView 销毁后不在处理新接收的位置
            if (bdLocation == null || mMapView == null) {
                return;
            }
            mCurLocation = bdLocation;//保存当前定位，后面检索算路要用
            int type = bdLocation.getLocType();
            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(bdLocation.getRadius())
                    // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(bdLocation.getDirection()).latitude(bdLocation.getLatitude())
                    .longitude(bdLocation.getLongitude()).build();
            BaiduMap bmap = mMapView.getMap();
            bmap.setMyLocationData(locData);
            /**
             *当首次定位或手动发起定位时，记得要放大地图，便于观察具体的位置
             * LatLng是缩放的中心点，这里注意一定要和上面设置给地图的经纬度一致；
             * MapStatus.Builder 地图状态构造器
             */
            if (isFirstLoc || autoLocation) {
                isFirstLoc = false;
                autoLocation = false;
                LatLng ll = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
                MapStatus.Builder builder = new MapStatus.Builder();
                //设置缩放中心点；缩放比例；
                builder.target(ll).zoom(18.0f);
                //给地图设置状态
                bmap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
            }
        }
    }

    //创建poi检索监听器
    OnGetPoiSearchResultListener poiSearchListener = new OnGetPoiSearchResultListener() {
        @Override
        public void onGetPoiResult(PoiResult poiResult) {
            //显示搜索结果
            List<PoiInfo> poiList=poiResult.getAllPoi();
            PoiAdapter adapter=new PoiAdapter(MainActivity.this,R.layout.poi_item,poiList);
            ListView listView=findViewById(R.id.searchResult);
            listView.setAdapter(adapter);
            listView.setVisibility(View.VISIBLE);

            //当滑动到底部时加载更多搜索结果
            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                        // 判断是否滚动到底部
                        if (view.getLastVisiblePosition() == view.getCount() - 1) {
                            //加载更多
                            int curPage=poiResult.getCurrentPageNum();
                            int totalPage=poiResult.getTotalPageNum();
                            if(curPage< totalPage)
                            {
                                poiResult.setCurrentPageNum(curPage+1);
                                String city=mCurLocation.getCity();
                                TextView textV=findViewById(R.id.inputText);
                                String keyWord=textV.getText().toString();
                                mPoiSearch.searchInCity(new PoiCitySearchOption()
                                        .city(city)
                                        .keyword(keyWord)
                                        .pageNum(curPage+1));
                            }
                        }
                    }
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

                }
            });
        }
        @Override
        public void onGetPoiDetailResult(PoiDetailSearchResult poiDetailSearchResult) {

        }
        @Override
        public void onGetPoiIndoorResult(PoiIndoorResult poiIndoorResult) {

        }
        //废弃
        @Override
        public void onGetPoiDetailResult(PoiDetailResult poiDetailResult) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SDKInitializer.setAgreePrivacy(getApplicationContext(), true);
        //获取地图控件引用
        mMapView = (MapView) findViewById(R.id.bmapView);

        //开启定位
        mMapView.getMap().setMyLocationEnabled(true);
        //定位初始化
        try {
            mLocationClient = new LocationClient(getApplicationContext());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        //通过LocationClientOption设置LocationClient相关参数
        LocationClientOption option = new LocationClientOption();
        option.setIsNeedAddress(true);
        option.setOpenGnss(true);
        option.setCoorType("bd09ll"); // 设置坐标类型
        option.setScanSpan(1000);
        //设置locationClientOption
        mLocationClient.setLocOption(option);
        //注册LocationListener监听器
        MyLocationListener myLocationListener = new MyLocationListener();
        mLocationClient.registerLocationListener(myLocationListener);

        //注册请求权限意图
        requestPermissionIntent = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean fineLocation = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
            boolean writeStorage = Boolean.TRUE.equals(result.get(Manifest.permission.WRITE_EXTERNAL_STORAGE));
        });

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //开始权限请求
            requestPermissionIntent.launch(permissions);
        }

        mLocationClient.start();

        //获得检索输入框控件
        mInputText=findViewById(R.id.inputText);
        mInputText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean ret=false;
                if(actionId== EditorInfo.IME_ACTION_SEARCH)
                {
                    String city=mCurLocation.getCity();
                    String keyWord=v.getText().toString();
                    ret=mPoiSearch.searchInCity(new PoiCitySearchOption()
                            .city(city)
                            .keyword(keyWord)
                            .pageNum(0));
                    //搜索后隐藏键盘
                    InputMethodManager imum=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                    View view=getWindow().peekDecorView();
                    if(view!=null)
                    {
                        imum.hideSoftInputFromWindow(view.getWindowToken(),0);
                    }
                }
                return ret;
            }
        });
        //创建poi检索实例
        mPoiSearch = PoiSearch.newInstance();
        //设置监听器
        mPoiSearch.setOnGetPoiSearchResultListener(poiSearchListener);

        //a关闭搜索结果
        ListView listView = findViewById(R.id.searchResult);
        listView.setVisibility(View.GONE);
        //显示开始导航按钮
        Button startNav=findViewById(R.id.startnavi);
        startNav.setVisibility(View.VISIBLE);

        //导航初始化
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(android.os.Environment.MEDIA_MOUNTED);//判断sd卡是否存在
        if(sdCardExist)
        {
            sdDir = Environment.getExternalStorageDirectory();//获取跟目录
        }

        if(!initDirs())
        {
            return;
        }

        BNaviInitConfig navInitCfg= new BNaviInitConfig.Builder().sdcardRootPath(mSDCardPath.toString())
                .naviInitListener(new IBaiduNaviManager.INaviInitListener() {
                    @Override
                    public void onAuthResult(int i, String s) {
                        if(i==0)
                        {
                            Toast.makeText(MainActivity.this, "key校验成功!", Toast.LENGTH_SHORT).show();
                        }
                        else if(i==1)
                        {
                            Toast.makeText(MainActivity.this, "key校验失败, " + s, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void initStart() {

                    }

                    @Override
                    public void initSuccess() {
                        Toast.makeText(MainActivity.this, "百度导航引擎初始化成功", Toast.LENGTH_SHORT).show();

                    }

                    @Override
                    public void initFailed(int i) {
                        Toast.makeText(MainActivity.this, "百度导航引擎初始化失败", Toast.LENGTH_SHORT).show();

                    }
                }).build();
        BaiduNaviManagerFactory.getBaiduNaviManager().init(getApplicationContext(),navInitCfg);

        //增加开始导航按钮的响应
        startNav=findViewById(R.id.startnavi);
        startNav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNavi(true);
            }
        });

    }

    private void startNavi(boolean isRealNavi) {
        if(mCurLocation==null||mDestation==null)
            return;
        BNRoutePlanNode sNode = new BNRoutePlanNode.Builder()
                .latitude(mCurLocation.getLatitude())
                .longitude(mCurLocation.getLongitude())
                .name("我的位置")
                .description("我的位置")
                .build();
        BNRoutePlanNode eNode = new BNRoutePlanNode.Builder()
                .latitude(mDestation.getLocation().latitude)
                .longitude(mDestation.getLocation().longitude)
                .name(mDestation.name)
                .description(mDestation.name)
                .build();
        List<BNRoutePlanNode> list = new ArrayList<>();
        list.add(sNode);
        list.add(eNode);
        BaiduNaviManagerFactory.getRoutePlanManager().routePlan(
                list,
                IBNRoutePlanManager.RoutePlanPreference.ROUTE_PLAN_PREFERENCE_DEFAULT,
                null,
                new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        switch (msg.what) {
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_START:
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "算路开始", Toast.LENGTH_SHORT).show();
                                break;
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_SUCCESS:
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "算路成功", Toast.LENGTH_SHORT).show();
                                break;
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_FAILED:
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "算路失败", Toast.LENGTH_SHORT).show();
                                break;
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_TO_NAVI:
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "算路成功准备进入导航", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(MainActivity.this.getApplicationContext(),
                                        DemoGuideActivity.class);
                                intent.putExtra("isRealNavi",isRealNavi);
                                startActivity(intent);
                                break;
                            default:
                                // nothing
                                break;
                        }
                    }

                });
    }

    private boolean initDirs() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mSDCardPath = Environment.getStorageDirectory();
        }
        if (mSDCardPath == null) {
            return false;
        }
        File f = new File(mSDCardPath, APP_FOLDER_NAME);
        if (!f.exists()) {
            try {
                f.mkdir();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            ListView listView = findViewById(R.id.searchResult);
            int left = listView.getLeft(), top = listView.getTop(), right = left + listView.getWidth(), bottom = top + listView.getHeight();
            if (ev.getX() < left || ev.getX() > right || ev.getY() < top || ev.getY() > bottom)//点击搜索结果列表之外区域，隐藏搜索结果列表
                listView.setVisibility(View.GONE);
        }
        return super.dispatchTouchEvent(ev);
    }

    public void setDestation(PoiInfo poi){
        mDestation=poi;
    }
}