# YiEventBus
My Event Bus

目前实现了onEvent方法,register方法

使用方法

已经上传Jcenter

Gradle

compile 'yiteng.libs:yibus:1.0.2'

1.0.2 更新说明
添加register时ThreadMode参数，具体使用如下：
YiBus.getDefault().register(this, ThreadMode.InMainThread);
