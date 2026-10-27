<script lang="ts" setup>
import { Toast, VButton, VModal, VSpace } from "@halo-dev/components";
import { computed, nextTick, ref, watch } from "vue";
import { axiosInstance } from "@halo-dev/api-client";
import cloneDeep from "lodash.clonedeep";
import type { MapGroup } from "@/types";
import GaodeModel from "./GaodeModel.vue";

const props = withDefaults(
  defineProps<{
    visible: boolean;
    group?: MapGroup;
  }>(),
  {
    visible: false,
    group: undefined,
  }
);

const emit = defineEmits<{
  (event: "update:visible", visible: boolean): void;
  (event: "close"): void;
}>();

const initialFormState: MapGroup = {
  apiVersion: "map.aiheiyo.top/v1alpha1",
  kind: "MapGroup",
  metadata: {
    name: "",
    generateName: "map-group-",
  },
  spec: {
    displayName: "",
    coordinates: "",
    priority: 0,
  },
};

const formState = ref<MapGroup>(cloneDeep(initialFormState));
const saving = ref(false);
const formVisible = ref(false);
const showMapParser = ref(false);
const coordinatesNode = ref<{ input(value: string): Promise<unknown> }>();

function applyCoordinates(coordinates: string) {
  if (coordinatesNode.value) {
    coordinatesNode.value.input(coordinates);
  } else {
    formState.value.spec.coordinates = coordinates;
  }
}

const isUpdateMode = computed(() => {
  return !!formState.value.metadata.creationTimestamp;
});

const modalTitle = computed(() => {
  return isUpdateMode.value ? "编辑地点" : "新建地点";
});

const annotationsFormRef = ref();

const handleCreateOrUpdateGroup = async () => {
  annotationsFormRef.value?.handleSubmit();
  await nextTick();

  const { customAnnotations, annotations, customFormInvalid, specFormInvalid } =
    annotationsFormRef.value || {};
  if (customFormInvalid || specFormInvalid) {
    return;
  }

  formState.value.metadata.annotations = {
    ...annotations,
    ...customAnnotations,
  };

  try {
    saving.value = true;
    if (isUpdateMode.value) {
      await axiosInstance.put(
        `/apis/map.aiheiyo.top/v1alpha1/mapgroups/${formState.value.metadata.name}`,
        formState.value
      );
    } else {
      await axiosInstance.post(
        "/apis/map.aiheiyo.top/v1alpha1/mapgroups",
        formState.value
      );
    }

    Toast.success("保存成功");

    onVisibleChange(false);
  } catch (e) {
    console.error("Failed to create map group", e);
  } finally {
    saving.value = false;
  }
};

const onVisibleChange = (visible: boolean) => {
  emit("update:visible", visible);
  if (!visible) {
    showMapParser.value = false;
    emit("close");
  }
};

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      if (props.group) formState.value = cloneDeep(props.group);
      formVisible.value = true;
    } else {
      showMapParser.value = false;
      setTimeout(() => {
        formVisible.value = false;
        formState.value = cloneDeep(initialFormState);
      }, 200);
    }
  }
);
</script>
<template>
  <VModal
    :visible="visible"
    :width="600"
    :title="modalTitle"
    @update:visible="onVisibleChange"
  >
    <FormKit
      v-if="formVisible"
      id="link-group-form"
      v-model="formState.spec"
      name="link-group-form"
      type="form"
      :config="{ validationVisibility: 'submit' }"
      @submit="handleCreateOrUpdateGroup"
    >
      <div class="md:grid md:grid-cols-4 md:gap-6">
        <div class="md:col-span-1">
          <div class="sticky top-0">
            <span class="text-base font-medium text-gray-900"> 常规 </span>
          </div>
        </div>
        <div class="mt-5 divide-y divide-gray-100 md:col-span-3 md:mt-0">
          <FormKit
            name="displayName"
            label="地点名称"
            type="text"
            validation="required"
          ></FormKit>
          <div>
            <div class="coordinates-label-row">
              <label for="map-group-coordinates"
                >坐标（经度,纬度）<span aria-hidden="true"> *</span></label
              >
              <button
                type="button"
                class="map-parser-button"
                @click="showMapParser = true"
              >
                地图解析
              </button>
            </div>
            <FormKit
              id="map-group-coordinates"
              name="coordinates"
              type="text"
              validation="required"
              validation-label="坐标（经度,纬度）"
              help="是否展示未关联文章的地点，可在插件设置的「地图显示」中调整。无需填写元数据。"
              @node="coordinatesNode = $event"
            ></FormKit>
          </div>
        </div>
      </div>
    </FormKit>

    <div class="py-5">
      <div class="border-t border-gray-200"></div>
    </div>

    <div class="md:grid md:grid-cols-4 md:gap-6">
      <div class="md:col-span-1">
        <div class="sticky top-0">
          <span class="text-base font-medium text-gray-900"> 元数据 </span>
        </div>
      </div>
      <div class="mt-5 divide-y divide-gray-100 md:col-span-3 md:mt-0">
        <AnnotationsForm
          v-if="visible"
          :key="formState.metadata.name"
          ref="annotationsFormRef"
          :value="formState.metadata.annotations"
          kind="MapGroup"
          group="map.aiheiyo.top"
        />
      </div>
    </div>

    <template #footer>
      <VSpace>
        <VButton
          :loading="saving"
          type="secondary"
          @click="$formkit.submit('link-group-form')"
        >
          提交
        </VButton>
        <VButton @click="onVisibleChange(false)"> 取消</VButton>
      </VSpace>
    </template>
  </VModal>
  <GaodeModel
    v-model:visible="showMapParser"
    :initial-coordinates="formState.spec.coordinates"
    @confirm="applyCoordinates"
  />
</template>

<style scoped>
.coordinates-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
}
.coordinates-label-row label span {
  color: #dc2626;
}
.map-parser-button {
  flex-shrink: 0;
  padding: 4px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: white;
  cursor: pointer;
  font-size: 12px;
}
.map-parser-button:hover {
  background: #f3f4f6;
}
</style>
