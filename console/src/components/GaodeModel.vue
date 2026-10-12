<script lang="ts" setup>
import { Toast, VButton, VModal, VSpace } from "@halo-dev/components";
import { consoleApiClient } from "@halo-dev/api-client";
import AMapLoader from "@amap/amap-jsapi-loader";
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";

type Location = { getLng(): number; getLat(): number };
type MapInstance = {
  on(event: string, handler: (event: { lnglat: Location }) => void): void;
  setCenter(location: Location): void;
  destroy(): void;
};
type SearchResult = {
  poiList?: { pois?: Array<{ location?: Location; name?: string }> };
};
type MapApi = {
  Map: new (container: HTMLElement, options: object) => MapInstance;
  Marker: new (options: object) => {
    setPosition(location: Location): void;
    setMap(map: MapInstance | null): void;
  };
  PlaceSearch: new (options: object) => {
    search(
      keyword: string,
      callback: (status: string, result: SearchResult) => void
    ): void;
  };
};

const props = defineProps<{ visible: boolean }>();
const emit = defineEmits<{
  (event: "update:visible", visible: boolean): void;
}>();

const container = ref<HTMLElement>();
const keyword = ref("");
const longitude = ref<number>();
const latitude = ref<number>();
const selectedAddress = ref("");
const loading = ref(false);
const searching = ref(false);
const error = ref("");
const ready = ref(false);
const coordinates = computed(() =>
  longitude.value === undefined || latitude.value === undefined
    ? ""
    : `${longitude.value},${latitude.value}`
);

let map: MapInstance | undefined;
let marker: InstanceType<MapApi["Marker"]> | undefined;
let placeSearch: InstanceType<MapApi["PlaceSearch"]> | undefined;
let requestId = 0;

function destroyMap() {
  requestId++;
  marker?.setMap(null);
  map?.destroy();
  map = undefined;
  marker = undefined;
  placeSearch = undefined;
  ready.value = false;
  loading.value = false;
  searching.value = false;
  longitude.value = undefined;
  latitude.value = undefined;
  selectedAddress.value = "";
  error.value = "";
}

function selectLocation(location: Location, address = "地图选点") {
  longitude.value = location.getLng();
  latitude.value = location.getLat();
  selectedAddress.value = address;
  marker?.setPosition(location);
  marker?.setMap(map ?? null);
  map?.setCenter(location);
}

async function initializeMap() {
  if (!props.visible || loading.value || ready.value) return;
  const currentRequestId = ++requestId;
  loading.value = true;
  error.value = "";
  try {
    const response = await consoleApiClient.plugin.plugin.fetchPluginConfig({
      name: "PluginMaps",
    });
    if (currentRequestId !== requestId) return;

    const config = response.data.data?.gaode;
    const gaode = typeof config === "string" ? JSON.parse(config) : config;
    if (!gaode?.key || !gaode?.securityJsCode) {
      throw new Error(
        "请先在插件设置的「地址解析」中填写高德 Key 和安全密钥。"
      );
    }

    (
      window as Window & { _AMapSecurityConfig?: { securityJsCode: string } }
    )._AMapSecurityConfig = { securityJsCode: gaode.securityJsCode };
    const api: MapApi = await AMapLoader.load({
      key: gaode.key,
      version: "2.0",
      plugins: ["AMap.PlaceSearch"],
    });
    if (currentRequestId !== requestId || !container.value) return;

    map = new api.Map(container.value, {
      viewMode: "2D",
      zoom: 11,
      center: [103.951002, 30.564412],
    });
    marker = new api.Marker({});
    placeSearch = new api.PlaceSearch({ pageSize: 1 });
    map.on("click", (event) => selectLocation(event.lnglat));
    ready.value = true;
  } catch (cause) {
    if (currentRequestId === requestId) {
      error.value =
        cause instanceof Error
          ? cause.message
          : "地图加载失败，请检查高德配置和网络连接。";
    }
  } finally {
    if (currentRequestId === requestId) loading.value = false;
  }
}

function search() {
  const address = keyword.value.trim();
  if (!address || !placeSearch || searching.value) return;

  searching.value = true;
  longitude.value = undefined;
  latitude.value = undefined;
  selectedAddress.value = "";
  marker?.setMap(null);
  const currentRequestId = requestId;
  placeSearch.search(address, (status, result) => {
    if (currentRequestId !== requestId) return;
    searching.value = false;
    const poi = result?.poiList?.pois?.[0];
    if (status === "complete" && poi?.location) {
      selectLocation(poi.location, poi.name ?? address);
    } else {
      Toast.warning("未找到地址，请补充城市或更详细的地址后重试。");
    }
  });
}

async function copy() {
  if (!coordinates.value) return;
  try {
    await navigator.clipboard.writeText(coordinates.value);
    Toast.success("坐标已复制，可粘贴到分组的坐标字段。");
  } catch {
    Toast.warning("复制失败，请手动选中坐标复制。");
  }
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) {
      destroyMap();
      return;
    }
    await nextTick();
    await initializeMap();
  }
);
onBeforeUnmount(destroyMap);
</script>

<template>
  <VModal
    title="地图解析"
    :visible="visible"
    :width="650"
    @update:visible="emit('update:visible', $event)"
  >
    <div class="map-parser">
      <form class="map-parser-row" @submit.prevent="search">
        <input
          v-model="keyword"
          placeholder="输入城市、地址或地点名称"
          aria-label="地址"
        />
        <VButton
          type="secondary"
          :disabled="!ready || !keyword.trim()"
          :loading="searching"
          @click="search"
        >
          搜索
        </VButton>
      </form>
      <p v-if="loading">正在加载地图…</p>
      <div v-if="error" role="alert">
        <p>{{ error }}</p>
        <VButton size="sm" @click="initializeMap">重试</VButton>
      </div>
      <div ref="container" class="map-parser-map"></div>
      <p v-if="selectedAddress">{{ selectedAddress }}</p>
      <div class="map-parser-row">
        <input
          :value="coordinates"
          readonly
          placeholder="经度,纬度"
          aria-label="坐标（经度,纬度）"
        />
        <VButton :disabled="!coordinates || searching" @click="copy"
          >复制坐标</VButton
        >
      </div>
      <p class="map-parser-help">
        搜索后自动获取坐标，也可以点击地图选点。坐标顺序为经度,纬度。
      </p>
    </div>
    <template #footer>
      <VSpace>
        <VButton @click="emit('update:visible', false)">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped>
.map-parser {
  display: grid;
  gap: 12px;
}
.map-parser-row {
  display: flex;
  gap: 8px;
}
.map-parser input {
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
}
.map-parser-map {
  width: 100%;
  height: 360px;
}
.map-parser-help {
  color: #6b7280;
  font-size: 12px;
}
</style>
