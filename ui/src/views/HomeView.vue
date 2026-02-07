<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, ref, watch } from 'vue'
import { utils } from '@halo-dev/ui-shared'

type RuleOption = {
  code: string
  label: string
}

type RuleConfig = {
  prefix: string
  serialWidth: number
  codePattern: string
  resetPerYear: boolean
  departments: RuleOption[]
  docTypes: RuleOption[]
}

type LedgerItem = {
  id: string
  fullCode: string
  deptCode: string
  docType: string
  status: string
  createdAt: string
  replacesCode?: string
  replacedByCode?: string
  dependencyCodes?: string
  remark?: string
}

type OperationLog = {
  id: string
  action: string
  operator: string
  targetId?: string
  targetCode?: string
  detail?: string
  createdAt: string
}

type Mode = 'query' | 'create' | 'update' | 'manage' | 'logs'
type FilterOption = {
  value: string
  label: string
}

const props = defineProps<{
  mode?: Mode
}>()

const currentMode = computed<Mode>(() => props.mode || 'query')

const modeTitles: Record<Mode, string> = {
  query: '编号查询',
  create: '编号创建',
  update: '编号修改',
  manage: '规则管理',
  logs: '操作日志',
}
const statusText: Record<string, string> = {
  REGISTERED: '已注册',
  BOUND: '已绑定',
  SUPERSEDED: '已替代',
  VOID: '已作废',
  DELETED: '已删除',
}
const actionText: Record<string, string> = {
  RULE_UPDATED: '规则更新',
  LEDGER_REGISTERED: '编号注册',
  LEDGER_UPDATED: '编号修改',
  LEDGER_MARKED_DELETED: '标记删除',
}

const baseUrl = '/apis/api.article-id-management.console/v1'
const loading = ref(false)
const savingRule = ref(false)
const previewing = ref(false)
const registering = ref(false)
const updatingLedger = ref(false)
const deletingLedgerId = ref('')
const message = ref('')

const rules = ref<RuleConfig>({
  prefix: 'MS',
  serialWidth: 4,
  codePattern: '{前缀}-{部门编码}/{文件类型}-{流水号}{子文件片段}/{年份}{修订片段}',
  resetPerYear: true,
  departments: [],
  docTypes: [],
})

const registerForm = ref({
  deptCode: '',
  docType: '',
  year: new Date().getFullYear(),
  serial: '',
  subSerial: '',
  rev: '',
  replacesCode: [] as string[],
  replacedByCode: [] as string[],
  dependencyCodes: [] as string[],
  remark: '',
})
const registerPick = ref({
  replacesCode: '',
  replacedByCode: '',
  dependencyCodes: '',
})

const updateForm = ref({
  id: '',
  status: 'REGISTERED',
  replacesCode: [] as string[],
  replacedByCode: [] as string[],
  dependencyCodes: [] as string[],
  remark: '',
})
const updatePick = ref({
  replacesCode: '',
  replacedByCode: '',
  dependencyCodes: '',
})

const previewCode = ref('')
const ledger = ref<LedgerItem[]>([])
const logs = ref<OperationLog[]>([])
const ledgerFilter = ref({
  deptCodes: [] as string[],
  docTypes: [] as string[],
  statuses: ['REGISTERED', 'BOUND', 'SUPERSEDED', 'VOID'] as string[],
  keyword: '',
})
const logFilter = ref({
  actions: [] as string[],
  operators: [] as string[],
  keyword: '',
  start: '',
  end: '',
})
const expandedFilterRows = ref<Record<string, boolean>>({
  ledgerDept: false,
  ledgerType: false,
  ledgerStatus: false,
  logAction: false,
  logOperator: false,
})

const deptOptions = computed(() => rules.value.departments || [])
const typeOptions = computed(() => rules.value.docTypes || [])
const relationOptions = computed(() =>
  ledger.value.map((item) => ({
    code: item.fullCode,
    label: `${item.fullCode}（${statusLabel(item.status)}）`,
    id: item.id,
  })),
)
const relationOptionsForUpdate = computed(() =>
  relationOptions.value.filter((item) => item.id !== updateForm.value.id),
)
const deptFilterOptions = computed<FilterOption[]>(() =>
  deptOptions.value.map((item) => ({ value: item.code, label: `${item.code} · ${item.label}` })),
)
const typeFilterOptions = computed<FilterOption[]>(() =>
  typeOptions.value.map((item) => ({ value: item.code, label: `${item.code} · ${item.label}` })),
)
const statusFilterOptions: FilterOption[] = [
  { value: 'REGISTERED', label: '已注册' },
  { value: 'BOUND', label: '已绑定' },
  { value: 'SUPERSEDED', label: '已替代' },
  { value: 'VOID', label: '已作废' },
  { value: 'DELETED', label: '已删除' },
]
const actionFilterOptions = computed<FilterOption[]>(() =>
  logActions.value.map((item) => ({ value: item, label: actionLabel(item) })),
)
const operatorFilterOptions = computed<FilterOption[]>(() =>
  logOperators.value.map((item) => ({ value: item, label: item })),
)
const filteredLedger = computed(() => {
  const deptCodes = ledgerFilter.value.deptCodes
  const docTypes = ledgerFilter.value.docTypes
  const statuses = ledgerFilter.value.statuses
  const keyword = ledgerFilter.value.keyword.trim().toLowerCase()

  return ledger.value.filter((item) => {
    if (deptCodes.length > 0 && !deptCodes.includes(item.deptCode)) {
      return false
    }
    if (docTypes.length > 0 && !docTypes.includes(item.docType)) {
      return false
    }
    if (statuses.length > 0 && !statuses.includes(item.status)) {
      return false
    }
    if (keyword && !item.fullCode.toLowerCase().includes(keyword)) {
      return false
    }
    return true
  })
})

const canView = computed(() => hasPermission(['article-id-management:view', 'article-id-management:create', 'article-id-management:modify', 'article-id-management:manage']))
const canCreate = computed(() => hasPermission(['article-id-management:create', 'article-id-management:manage']))
const canModify = computed(() => hasPermission(['article-id-management:modify', 'article-id-management:manage']))
const canManage = computed(() => hasPermission(['article-id-management:manage']))
const canOperate = computed(() => canModify.value || canManage.value)
const logActions = computed(() => Array.from(new Set(logs.value.map((item) => item.action).filter(Boolean))))
const logOperators = computed(() => Array.from(new Set(logs.value.map((item) => item.operator).filter(Boolean))))
const filteredLogs = computed(() => {
  const actions = logFilter.value.actions
  const operators = logFilter.value.operators
  const keyword = logFilter.value.keyword.trim().toLowerCase()
  const startAt = logFilter.value.start ? new Date(`${logFilter.value.start}T00:00:00`).getTime() : null
  const endAt = logFilter.value.end ? new Date(`${logFilter.value.end}T23:59:59`).getTime() : null

  return logs.value.filter((item) => {
    if (actions.length > 0 && !actions.includes(item.action)) {
      return false
    }
    if (operators.length > 0 && !operators.includes(item.operator || '')) {
      return false
    }
    if (keyword && !(item.targetCode || '').toLowerCase().includes(keyword)) {
      return false
    }
    if (startAt || endAt) {
      const current = new Date(item.createdAt).getTime()
      if (startAt && current < startAt) {
        return false
      }
      if (endAt && current > endAt) {
        return false
      }
    }
    return true
  })
})

function hasPermission(permissions: string[]) {
  try {
    return utils.permission.has(permissions, true)
  } catch {
    return true
  }
}

function normalizeOptions(items: Array<{ code?: string; label?: string; name?: string }>) {
  return (items || []).map((item) => ({
    code: item.code || '',
    label: item.label || item.name || '',
  }))
}

const loadAll = async () => {
  loading.value = true
  try {
    const [ruleResp, ledgerResp] = await Promise.all([
      axios.get(`${baseUrl}/rules`),
      axios.get(`${baseUrl}/ledger`),
    ])
    rules.value = {
      codePattern: '{前缀}-{部门编码}/{文件类型}-{流水号}{子文件片段}/{年份}{修订片段}',
      ...ruleResp.data,
      departments: normalizeOptions(ruleResp.data.departments),
      docTypes: normalizeOptions(ruleResp.data.docTypes),
    }
    ledger.value = ledgerResp.data
    try {
      const logResp = await axios.get(`${baseUrl}/logs`)
      logs.value = logResp.data
    } catch {
      logs.value = []
    }

    if (!registerForm.value.deptCode && deptOptions.value.length > 0) {
      registerForm.value.deptCode = deptOptions.value[0].code
    }
    if (!registerForm.value.docType && typeOptions.value.length > 0) {
      registerForm.value.docType = typeOptions.value[0].code
    }
  } catch (err: any) {
    message.value = err?.response?.data?.message || '数据加载失败'
  } finally {
    loading.value = false
  }
}

const addOption = (target: 'departments' | 'docTypes') => {
  rules.value[target].push({ code: '', label: '' })
}

const removeOption = (target: 'departments' | 'docTypes', idx: number) => {
  rules.value[target].splice(idx, 1)
}

const saveRules = async () => {
  savingRule.value = true
  message.value = ''
  try {
    const payload = {
      ...rules.value,
      departments: rules.value.departments.filter((item) => item.code && item.label),
      docTypes: rules.value.docTypes.filter((item) => item.code && item.label),
    }
    const resp = await axios.put(`${baseUrl}/rules`, payload)
    rules.value = {
      ...resp.data,
      departments: normalizeOptions(resp.data.departments),
      docTypes: normalizeOptions(resp.data.docTypes),
    }
    message.value = '规则配置已保存'
  } catch (err: any) {
    message.value = err?.response?.data?.message || '规则配置保存失败'
  } finally {
    savingRule.value = false
  }
}

const buildPayload = () => ({
  deptCode: registerForm.value.deptCode,
  docType: registerForm.value.docType,
  year: Number(registerForm.value.year),
  serial: registerForm.value.serial ? Number(registerForm.value.serial) : null,
  subSerial: registerForm.value.subSerial ? Number(registerForm.value.subSerial) : null,
  rev: registerForm.value.rev ? Number(registerForm.value.rev) : null,
  replacesCode: joinCodes(registerForm.value.replacesCode),
  replacedByCode: joinCodes(registerForm.value.replacedByCode),
  dependencyCodes: joinCodes(registerForm.value.dependencyCodes),
  remark: registerForm.value.remark || null,
})

const preview = async () => {
  previewing.value = true
  message.value = ''
  try {
    const resp = await axios.post(`${baseUrl}/ledger/preview`, buildPayload())
    previewCode.value = resp.data.fullCode
  } catch (err: any) {
    message.value = err?.response?.data?.message || '编号预览失败'
    previewCode.value = ''
  } finally {
    previewing.value = false
  }
}

const registerCode = async () => {
  registering.value = true
  message.value = ''
  try {
    await axios.post(`${baseUrl}/ledger/register`, buildPayload())
    message.value = '编号注册成功'
    previewCode.value = ''
    registerForm.value.serial = ''
    registerForm.value.subSerial = ''
    registerForm.value.rev = ''
    registerForm.value.replacesCode = []
    registerForm.value.replacedByCode = []
    registerForm.value.dependencyCodes = []
    registerPick.value.replacesCode = ''
    registerPick.value.replacedByCode = ''
    registerPick.value.dependencyCodes = ''
    registerForm.value.remark = ''
    await loadAll()
  } catch (err: any) {
    message.value = err?.response?.data?.message || '编号注册失败'
  } finally {
    registering.value = false
  }
}

const pickUpdateTarget = (item: LedgerItem) => {
  updateForm.value.id = item.id
  updateForm.value.status = item.status
  updateForm.value.replacesCode = splitCodes(item.replacesCode)
  updateForm.value.replacedByCode = splitCodes(item.replacedByCode)
  updateForm.value.dependencyCodes = splitCodes(item.dependencyCodes)
  updateForm.value.remark = item.remark || ''
}

const updateLedger = async () => {
  if (!updateForm.value.id) {
    message.value = '请先从台账中选择一条记录'
    return
  }
  updatingLedger.value = true
  message.value = ''
  try {
    await axios.patch(`${baseUrl}/ledger/${updateForm.value.id}`, {
      status: updateForm.value.status,
      replacesCode: joinCodes(updateForm.value.replacesCode),
      replacedByCode: joinCodes(updateForm.value.replacedByCode),
      dependencyCodes: joinCodes(updateForm.value.dependencyCodes),
      remark: updateForm.value.remark,
    })
    message.value = '编号更新成功'
    await loadAll()
  } catch (err: any) {
    message.value = err?.response?.data?.message || '编号更新失败'
  } finally {
    updatingLedger.value = false
  }
}

const markDeleted = async (item: LedgerItem) => {
  deletingLedgerId.value = item.id
  message.value = ''
  try {
    await axios.post(`${baseUrl}/ledger/${item.id}/mark-delete`)
    message.value = '已标记删除'
    if (updateForm.value.id === item.id) {
      updateForm.value.status = 'DELETED'
    }
    await loadAll()
  } catch (err: any) {
    message.value = err?.response?.data?.message || '标记删除失败'
  } finally {
    deletingLedgerId.value = ''
  }
}

const statusLabel = (status: string) => statusText[status] || status
const actionLabel = (action: string) => actionText[action] || action
const addRelation = (target: string[], code: string) => {
  const normalized = (code || '').trim()
  if (!normalized) return
  if (!target.includes(normalized)) {
    target.push(normalized)
  }
}
const removeRelation = (target: string[], code: string) => {
  const idx = target.indexOf(code)
  if (idx >= 0) {
    target.splice(idx, 1)
  }
}
const splitCodes = (text?: string) =>
  (text || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
const joinCodes = (arr?: string[]) => {
  const values = (arr || []).map((item) => item.trim()).filter(Boolean)
  return values.length > 0 ? values.join(',') : null
}

const resetLogFilter = () => {
  logFilter.value = {
    actions: [],
    operators: [],
    keyword: '',
    start: '',
    end: '',
  }
}
const resetLedgerFilter = () => {
  ledgerFilter.value = {
    deptCodes: [],
    docTypes: [],
    statuses: ['REGISTERED', 'BOUND', 'SUPERSEDED', 'VOID'],
    keyword: '',
  }
}

const toggleFilterValue = (target: string[], value: string) => {
  const idx = target.indexOf(value)
  if (idx >= 0) {
    target.splice(idx, 1)
  } else {
    target.push(value)
  }
}
const isFilterValueSelected = (target: string[], value: string) => target.includes(value)
const getVisibleFilterOptions = (_key: string, options: FilterOption[]) => options
const getHiddenFilterCount = (key: string, options: FilterOption[]) =>
  Math.max(0, options.length - getVisibleFilterOptions(key, options).length)
const toggleFilterRowExpand = (key: string) => {
  expandedFilterRows.value[key] = !expandedFilterRows.value[key]
}

watch(currentMode, () => {
  message.value = ''
})

onMounted(loadAll)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <h1>文章编号管理</h1>
      <p>{{ modeTitles[currentMode] }} · 子菜单按权限显示</p>
    </header>

    <p v-if="message" class="message">{{ message }}</p>

    <section v-if="!canView && !canCreate && !canModify && !canManage" class="card">
      <h2>无权限访问</h2>
      <p>当前账号没有文章编号插件权限，请联系管理员分配。</p>
    </section>

    <section v-if="currentMode === 'manage' && canManage" class="card">
      <h2>规则配置</h2>
      <div class="grid compact">
        <label>
          前缀
          <input v-model="rules.prefix" class="input-editable" type="text" />
        </label>
        <label>
          流水号位数
          <input v-model.number="rules.serialWidth" class="input-editable" type="number" min="2" max="8" />
        </label>
        <label class="checkbox">
          <input v-model="rules.resetPerYear" type="checkbox" />
          按年份重置流水号
        </label>
      </div>
      <label>
        编号显示规则
        <input
          v-model="rules.codePattern"
          class="input-editable mono"
          type="text"
          placeholder="{前缀}-{部门编码}/{文件类型}-{流水号}{子文件片段}/{年份}{修订片段}"
        />
      </label>
      <p class="hint">
        支持占位符：
        {前缀} {部门编码} {文件类型} {流水号} {子文件号} {年份} {修订号} {子文件片段} {修订片段}
      </p>

      <div class="option-editor-wrap">
        <div class="option-editor">
          <div class="option-header">
            <h3>部门编码</h3>
            <button @click="addOption('departments')">新增</button>
          </div>
          <div class="option-list">
            <div v-for="(item, idx) in rules.departments" :key="`dep-${idx}`" class="option-row">
              <input v-model="item.code" placeholder="code" />
              <input v-model="item.label" placeholder="label" />
              <button class="danger" @click="removeOption('departments', idx)">删除</button>
            </div>
          </div>
        </div>

        <div class="option-editor">
          <div class="option-header">
            <h3>文件类型</h3>
            <button @click="addOption('docTypes')">新增</button>
          </div>
          <div class="option-list">
            <div v-for="(item, idx) in rules.docTypes" :key="`type-${idx}`" class="option-row">
              <input v-model="item.code" placeholder="code" />
              <input v-model="item.label" placeholder="label" />
              <button class="danger" @click="removeOption('docTypes', idx)">删除</button>
            </div>
          </div>
        </div>
      </div>

      <button :disabled="savingRule" @click="saveRules">{{ savingRule ? '保存中...' : '保存规则' }}</button>
    </section>

    <section v-if="currentMode === 'create' && canCreate" class="card">
      <h2>注册编号</h2>
      <div class="grid">
        <label>
          部门
          <select v-model="registerForm.deptCode">
            <option v-for="item in deptOptions" :key="item.code" :value="item.code">
              {{ item.code }} · {{ item.label }}
            </option>
          </select>
        </label>
        <label>
          文件类型
          <select v-model="registerForm.docType">
            <option v-for="item in typeOptions" :key="item.code" :value="item.code">
              {{ item.code }} · {{ item.label }}
            </option>
          </select>
        </label>
        <label>
          年份
          <input v-model.number="registerForm.year" type="number" min="2000" max="2100" />
        </label>
        <label>
          流水号（可空=自动）
          <input v-model="registerForm.serial" type="number" min="1" />
        </label>
        <label>
          子文件号（可空）
          <input v-model="registerForm.subSerial" type="number" min="1" />
        </label>
        <label>
          Rev（可空）
          <input v-model="registerForm.rev" type="number" min="1" />
        </label>
        <label>
          替代（来源编号）
          <div class="relation-picker">
            <select v-model="registerPick.replacesCode">
              <option value="">请选择编号</option>
              <option v-for="option in relationOptions" :key="`rep-${option.id}`" :value="option.code">
                {{ option.label }}
              </option>
            </select>
            <button type="button" class="small" @click="addRelation(registerForm.replacesCode, registerPick.replacesCode)">添加</button>
          </div>
          <div class="chips">
            <span v-for="code in registerForm.replacesCode" :key="`c-rep-${code}`" class="chip">
              {{ code }}
              <button type="button" class="chip-remove" @click="removeRelation(registerForm.replacesCode, code)">×</button>
            </span>
          </div>
        </label>
        <label>
          被替代（去向编号）
          <div class="relation-picker">
            <select v-model="registerPick.replacedByCode">
              <option value="">请选择编号</option>
              <option v-for="option in relationOptions" :key="`repby-${option.id}`" :value="option.code">
                {{ option.label }}
              </option>
            </select>
            <button type="button" class="small" @click="addRelation(registerForm.replacedByCode, registerPick.replacedByCode)">添加</button>
          </div>
          <div class="chips">
            <span v-for="code in registerForm.replacedByCode" :key="`c-repby-${code}`" class="chip">
              {{ code }}
              <button type="button" class="chip-remove" @click="removeRelation(registerForm.replacedByCode, code)">×</button>
            </span>
          </div>
        </label>
        <label>
          依赖
          <div class="relation-picker">
            <select v-model="registerPick.dependencyCodes">
              <option value="">请选择编号</option>
              <option v-for="option in relationOptions" :key="`dep-${option.id}`" :value="option.code">
                {{ option.label }}
              </option>
            </select>
            <button type="button" class="small" @click="addRelation(registerForm.dependencyCodes, registerPick.dependencyCodes)">添加</button>
          </div>
          <div class="chips">
            <span v-for="code in registerForm.dependencyCodes" :key="`c-dep-${code}`" class="chip">
              {{ code }}
              <button type="button" class="chip-remove" @click="removeRelation(registerForm.dependencyCodes, code)">×</button>
            </span>
          </div>
        </label>
      </div>
      <label>
        备注
        <input v-model="registerForm.remark" type="text" />
      </label>
      <div class="actions">
        <button :disabled="previewing" @click="preview">{{ previewing ? '预览中...' : '预览编号' }}</button>
        <button :disabled="registering" @click="registerCode">{{ registering ? '提交中...' : '注册编号' }}</button>
      </div>
      <p v-if="previewCode" class="preview">预览结果：{{ previewCode }}</p>
    </section>

    <section v-if="currentMode === 'update' && canModify" class="card">
      <h2>修改编号</h2>
      <div class="grid compact">
        <label>
          记录 ID
          <input v-model="updateForm.id" type="text" readonly />
        </label>
        <label>
          状态
          <select v-model="updateForm.status">
            <option value="REGISTERED">已注册</option>
            <option value="BOUND">已绑定</option>
            <option value="SUPERSEDED">已替代</option>
            <option value="VOID">已作废</option>
            <option value="DELETED">已删除</option>
          </select>
        </label>
      </div>
      <label>
        备注
        <input v-model="updateForm.remark" type="text" />
      </label>
      <label>
        替代（来源编号）
        <div class="relation-picker">
          <select v-model="updatePick.replacesCode">
            <option value="">请选择编号</option>
            <option v-for="option in relationOptionsForUpdate" :key="`u-rep-${option.id}`" :value="option.code">
              {{ option.label }}
            </option>
          </select>
          <button type="button" class="small" @click="addRelation(updateForm.replacesCode, updatePick.replacesCode)">添加</button>
        </div>
        <div class="chips">
          <span v-for="code in updateForm.replacesCode" :key="`u-c-rep-${code}`" class="chip">
            {{ code }}
            <button type="button" class="chip-remove" @click="removeRelation(updateForm.replacesCode, code)">×</button>
          </span>
        </div>
      </label>
      <label>
        被替代（去向编号）
        <div class="relation-picker">
          <select v-model="updatePick.replacedByCode">
            <option value="">请选择编号</option>
            <option v-for="option in relationOptionsForUpdate" :key="`u-repby-${option.id}`" :value="option.code">
              {{ option.label }}
            </option>
          </select>
          <button type="button" class="small" @click="addRelation(updateForm.replacedByCode, updatePick.replacedByCode)">添加</button>
        </div>
        <div class="chips">
          <span v-for="code in updateForm.replacedByCode" :key="`u-c-repby-${code}`" class="chip">
            {{ code }}
            <button type="button" class="chip-remove" @click="removeRelation(updateForm.replacedByCode, code)">×</button>
          </span>
        </div>
      </label>
      <label>
        依赖
        <div class="relation-picker">
          <select v-model="updatePick.dependencyCodes">
            <option value="">请选择编号</option>
            <option v-for="option in relationOptionsForUpdate" :key="`u-dep-${option.id}`" :value="option.code">
              {{ option.label }}
            </option>
          </select>
          <button type="button" class="small" @click="addRelation(updateForm.dependencyCodes, updatePick.dependencyCodes)">添加</button>
        </div>
        <div class="chips">
          <span v-for="code in updateForm.dependencyCodes" :key="`u-c-dep-${code}`" class="chip">
            {{ code }}
            <button type="button" class="chip-remove" @click="removeRelation(updateForm.dependencyCodes, code)">×</button>
          </span>
        </div>
      </label>
      <button :disabled="updatingLedger" @click="updateLedger">{{ updatingLedger ? '更新中...' : '保存修改' }}</button>
      <p class="hint">请先在下方台账列表点击“选中”后再修改。</p>
    </section>

    <section v-if="currentMode !== 'logs' && (canView || canCreate || canModify || canManage)" class="card">
      <h2>编号台账</h2>
      <div class="filter-panel ledger-filter">
        <div class="filter-row">
          <div class="filter-title">部门</div>
          <div class="filter-values" :class="{ expanded: expandedFilterRows.ledgerDept }">
            <button
              v-for="item in getVisibleFilterOptions('ledgerDept', deptFilterOptions)"
              :key="`filter-dept-${item.value}`"
              type="button"
              class="filter-pill"
              :class="{ active: isFilterValueSelected(ledgerFilter.deptCodes, item.value) }"
              @click="toggleFilterValue(ledgerFilter.deptCodes, item.value)"
            >
              {{ item.label }}
            </button>
            <button
              v-if="getHiddenFilterCount('ledgerDept', deptFilterOptions) > 0"
              type="button"
              class="filter-pill expand"
              @click="toggleFilterRowExpand('ledgerDept')"
            >
              {{ expandedFilterRows.ledgerDept ? '收起' : `+${getHiddenFilterCount('ledgerDept', deptFilterOptions)}` }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <div class="filter-title">文件类型</div>
          <div class="filter-values" :class="{ expanded: expandedFilterRows.ledgerType }">
            <button
              v-for="item in getVisibleFilterOptions('ledgerType', typeFilterOptions)"
              :key="`filter-type-${item.value}`"
              type="button"
              class="filter-pill"
              :class="{ active: isFilterValueSelected(ledgerFilter.docTypes, item.value) }"
              @click="toggleFilterValue(ledgerFilter.docTypes, item.value)"
            >
              {{ item.label }}
            </button>
            <button
              v-if="getHiddenFilterCount('ledgerType', typeFilterOptions) > 0"
              type="button"
              class="filter-pill expand"
              @click="toggleFilterRowExpand('ledgerType')"
            >
              {{ expandedFilterRows.ledgerType ? '收起' : `+${getHiddenFilterCount('ledgerType', typeFilterOptions)}` }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <div class="filter-title">状态</div>
          <div class="filter-values" :class="{ expanded: expandedFilterRows.ledgerStatus }">
            <button
              v-for="item in getVisibleFilterOptions('ledgerStatus', statusFilterOptions)"
              :key="`filter-status-${item.value}`"
              type="button"
              class="filter-pill"
              :class="{ active: isFilterValueSelected(ledgerFilter.statuses, item.value) }"
              @click="toggleFilterValue(ledgerFilter.statuses, item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
      </div>
      <div class="grid ledger-filter-inputs">
        <label>
          编号关键词
          <input v-model="ledgerFilter.keyword" type="text" placeholder="输入编号关键字" />
        </label>
        <div class="ledger-filter-actions">
          <button type="button" class="small" @click="resetLedgerFilter">重置筛选</button>
        </div>
      </div>
      <div v-if="loading">加载中...</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>部门</th>
              <th>类型</th>
              <th>状态</th>
              <th>替代</th>
              <th>被替代</th>
              <th>依赖</th>
              <th>创建时间</th>
              <th v-if="canOperate">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredLedger" :key="item.id">
              <td>{{ item.fullCode }}</td>
              <td>{{ item.deptCode }}</td>
              <td>{{ item.docType }}</td>
              <td>{{ statusLabel(item.status) }}</td>
              <td>{{ item.replacesCode || '-' }}</td>
              <td>{{ item.replacedByCode || '-' }}</td>
              <td>{{ item.dependencyCodes || '-' }}</td>
              <td>{{ item.createdAt }}</td>
              <td v-if="canOperate" class="action-cell">
                <button v-if="canModify" class="small" @click="pickUpdateTarget(item)">选中</button>
                <button
                  class="small danger"
                  :disabled="item.status === 'DELETED' || deletingLedgerId === item.id"
                  @click="markDeleted(item)"
                >
                  {{ deletingLedgerId === item.id ? '处理中...' : '标记删除' }}
                </button>
              </td>
            </tr>
            <tr v-if="filteredLedger.length === 0">
              <td :colspan="canOperate ? 9 : 8">暂无数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="currentMode === 'logs' && (canView || canManage)" class="card">
      <h2>操作日志</h2>
      <div class="filter-panel log-filter">
        <div class="filter-row">
          <div class="filter-title">操作类型</div>
          <div class="filter-values" :class="{ expanded: expandedFilterRows.logAction }">
            <button
              v-for="item in getVisibleFilterOptions('logAction', actionFilterOptions)"
              :key="`log-action-${item.value}`"
              type="button"
              class="filter-pill"
              :class="{ active: isFilterValueSelected(logFilter.actions, item.value) }"
              @click="toggleFilterValue(logFilter.actions, item.value)"
            >
              {{ item.label }}
            </button>
            <button
              v-if="getHiddenFilterCount('logAction', actionFilterOptions) > 0"
              type="button"
              class="filter-pill expand"
              @click="toggleFilterRowExpand('logAction')"
            >
              {{ expandedFilterRows.logAction ? '收起' : `+${getHiddenFilterCount('logAction', actionFilterOptions)}` }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <div class="filter-title">操作人</div>
          <div class="filter-values" :class="{ expanded: expandedFilterRows.logOperator }">
            <button
              v-for="item in getVisibleFilterOptions('logOperator', operatorFilterOptions)"
              :key="`log-operator-${item.value}`"
              type="button"
              class="filter-pill"
              :class="{ active: isFilterValueSelected(logFilter.operators, item.value) }"
              @click="toggleFilterValue(logFilter.operators, item.value)"
            >
              {{ item.label }}
            </button>
            <button
              v-if="getHiddenFilterCount('logOperator', operatorFilterOptions) > 0"
              type="button"
              class="filter-pill expand"
              @click="toggleFilterRowExpand('logOperator')"
            >
              {{ expandedFilterRows.logOperator ? '收起' : `+${getHiddenFilterCount('logOperator', operatorFilterOptions)}` }}
            </button>
          </div>
        </div>
      </div>
      <div class="grid log-filter-inputs">
        <label>
          编号关键词
          <input v-model="logFilter.keyword" type="text" placeholder="输入编号关键字" />
        </label>
        <label>
          开始日期
          <input v-model="logFilter.start" type="date" />
        </label>
        <label>
          结束日期
          <input v-model="logFilter.end" type="date" />
        </label>
        <div class="log-filter-actions">
          <button type="button" class="small" @click="resetLogFilter">重置筛选</button>
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>操作</th>
              <th>编号</th>
              <th>操作人</th>
              <th>时间</th>
              <th>详情</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredLogs" :key="item.id">
              <td>{{ actionLabel(item.action) }}</td>
              <td>{{ item.targetCode || '-' }}</td>
              <td>{{ item.operator || '-' }}</td>
              <td>{{ item.createdAt }}</td>
              <td>{{ item.detail || '-' }}</td>
            </tr>
            <tr v-if="filteredLogs.length === 0">
              <td colspan="5">暂无日志</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  width: 100%;
  max-width: none;
  margin: 0;
  padding: 24px 28px;
  color: #0f172a;
  min-height: 100vh;
  box-sizing: border-box;
  font-size: 14px;
}

.page-header {
  margin-bottom: 18px;
  width: 100%;
}

.page-header h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.15;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.page-header p {
  margin: 8px 0 0;
  color: #475569;
  font-size: 14px;
}

.message {
  color: #0f766e;
  margin: 10px 0 16px;
  font-size: 13px;
  font-weight: 600;
  background: #ecfeff;
  border: 1px solid #99f6e4;
  border-radius: 10px;
  padding: 10px 12px;
}

.card {
  background: #ffffff;
  border: 1px solid #d7dfeb;
  border-radius: 14px;
  padding: 20px;
  margin-bottom: 18px;
  width: 100%;
  box-sizing: border-box;
  box-shadow: 0 1px 1px rgba(15, 23, 42, 0.02), 0 8px 24px rgba(15, 23, 42, 0.04);
}

.card h2 {
  margin: 0 0 14px;
  font-size: 28px;
  line-height: 1.2;
  font-weight: 700;
  color: #0b1220;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.grid.compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.page :is(input:not([type='checkbox']), select, textarea) {
  border: 1px solid #c3d2e8 !important;
  border-radius: 10px !important;
  padding: 0 13px !important;
  font-size: 14px;
  background: #f8fbff !important;
  color: #0f172a !important;
  box-sizing: border-box;
  box-shadow: none !important;
}

.page :is(input:not([type='checkbox']), select) {
  height: 44px !important;
  min-height: 44px !important;
  line-height: 42px !important;
}

.page textarea {
  min-height: 120px;
  padding: 10px 12px !important;
  line-height: 1.4;
}

.page input[type='number'] {
  appearance: textfield;
}

.page :is(input:not([type='checkbox']), select, textarea):focus {
  outline: none;
  border-color: #3b82f6 !important;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.14) !important;
}

.page input[readonly] {
  background: #f1f5f9 !important;
  border-color: #d1d9e6 !important;
  color: #475569 !important;
}

.mono {
  font-family: Consolas, 'Courier New', monospace;
}

.checkbox {
  flex-direction: row;
  align-items: center;
  margin-top: 24px;
}

.actions {
  margin-top: 14px;
  display: flex;
  gap: 10px;
}

.relation-picker {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.chips {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #bcd2ff;
  background: #eaf2ff;
  color: #87a3ff;
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  line-height: 1;
}

.chip-remove {
  border: 0;
  background: transparent;
  color: #87a3ff;
  font-size: 14px;
  line-height: 1;
  padding: 0;
  cursor: pointer;
}

button {
  border: 1px solid #2563eb;
  border-radius: 10px;
  background: #2563eb;
  color: #fff;
  padding: 9px 15px;
  font-weight: 600;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.18s ease;
}


button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

button.small {
  padding: 6px 11px;
  border-radius: 8px;
  font-size: 12px;
}

.relation-picker button.small {
  min-width: 56px;
  height: 44px;
  line-height: 1;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
}

.relation-picker select {
  min-width: 0;
}

button.danger {
  background: #dc2626;
  border-color: #dc2626;
}

button.danger:hover:not(:disabled) {
  background: #b91c1c;
  border-color: #b91c1c;
}

.option-editor-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 14px 0;
}

.option-editor {
  border: 1px solid #d9e1ee;
  border-radius: 12px;
  padding: 14px;
  background: #fafcff;
}

.option-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.option-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.option-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow: auto;
}

.option-row {
  display: grid;
  grid-template-columns: 0.9fr 1.1fr auto;
  gap: 8px;
}

.preview {
  margin-top: 10px;
  font-weight: 700;
  color: #1d4ed8;
}

.hint {
  color: #64748b;
  margin-top: 8px;
  font-size: 12px;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 720px;
}

.table-wrap {
  width: 100%;
  overflow-x: auto;
}

th,
td {
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
  padding: 11px 8px;
  font-size: 13px;
}

th {
  font-weight: 700;
  color: #334155;
  background: #f8fafc;
}

td {
  color: #0f172a;
}

.action-cell {
  display: flex;
  gap: 8px;
  align-items: center;
}

.filter-panel {
  margin-bottom: 14px;
  padding: 12px;
  border: 1px dashed #d7dfeb;
  border-radius: 12px;
  background: #fbfdff;
}

.filter-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  margin-bottom: 10px;
}

.filter-row:last-child {
  margin-bottom: 0;
}

.filter-title {
  font-size: 13px;
  line-height: 32px;
  color: #334155;
  font-weight: 700;
}

.filter-values {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  white-space: normal;
}

.filter-pill {
  border: 1px solid #cbd5e1;
  background: #f8fafc;
  color: #334155;
  border-radius: 9px;
  height: 32px;
  line-height: 30px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  flex: 0 0 auto;
}

.filter-pill:hover {
  border-color: #9bb8e9;
  background: #d6e2fcff;
}

.filter-pill.active {
  background: #e9f1ff;
  border-color: #8db5ff;
  color: #1e40af;
  box-shadow: inset 0 0 0 1px rgba(59, 130, 246, 0.1);
}

.filter-pill.expand {
  background: #f6f8fc;
  border-color: #d4dbe8;
  color: #334155;
}

.ledger-filter {
  margin-bottom: 12px;
}

.log-filter-inputs,
.ledger-filter-inputs {
  margin-bottom: 12px;
}

.log-filter-actions,
.ledger-filter-actions {
  display: flex;
  align-items: flex-end;
}

.log-filter-actions button.small,
.ledger-filter-actions button.small {
  height: 44px;
  min-height: 44px;
  padding: 0 16px;
}

@media (max-width: 1000px) {
  .grid,
  .grid.compact,
  .option-editor-wrap {
    grid-template-columns: 1fr;
  }

  .filter-row {
    grid-template-columns: 1fr;
  }

  .filter-title {
    line-height: 1.4;
  }

  .option-row {
    grid-template-columns: 1fr;
  }
}
</style>
