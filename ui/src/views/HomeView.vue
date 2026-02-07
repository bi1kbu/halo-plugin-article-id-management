<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, ref } from 'vue'
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

type Mode = 'query' | 'create' | 'update' | 'manage'

const props = defineProps<{
  mode?: Mode
}>()

const currentMode = computed<Mode>(() => props.mode || 'query')

const modeTitles: Record<Mode, string> = {
  query: '编号查询',
  create: '编号创建',
  update: '编号修改',
  manage: '规则管理',
}
const statusText: Record<string, string> = {
  REGISTERED: '已注册',
  BOUND: '已绑定',
  SUPERSEDED: '已替代',
  VOID: '已作废',
}

const baseUrl = '/apis/api.article-id-management.console/v1'
const loading = ref(false)
const savingRule = ref(false)
const previewing = ref(false)
const registering = ref(false)
const updatingLedger = ref(false)
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

const canView = computed(() => hasPermission(['article-id-management:view', 'article-id-management:create', 'article-id-management:modify', 'article-id-management:manage']))
const canCreate = computed(() => hasPermission(['article-id-management:create', 'article-id-management:manage']))
const canModify = computed(() => hasPermission(['article-id-management:modify', 'article-id-management:manage']))
const canManage = computed(() => hasPermission(['article-id-management:manage']))

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

    if (!registerForm.value.deptCode && deptOptions.value.length > 0) {
      registerForm.value.deptCode = deptOptions.value[0].code
    }
    if (!registerForm.value.docType && typeOptions.value.length > 0) {
      registerForm.value.docType = typeOptions.value[0].code
    }
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

const statusLabel = (status: string) => statusText[status] || status
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

    <section v-if="canView || canCreate || canModify || canManage" class="card">
      <h2>编号台账</h2>
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
              <th v-if="canModify">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in ledger" :key="item.id">
              <td>{{ item.fullCode }}</td>
              <td>{{ item.deptCode }}</td>
              <td>{{ item.docType }}</td>
              <td>{{ statusLabel(item.status) }}</td>
              <td>{{ item.replacesCode || '-' }}</td>
              <td>{{ item.replacedByCode || '-' }}</td>
              <td>{{ item.dependencyCodes || '-' }}</td>
              <td>{{ item.createdAt }}</td>
              <td v-if="canModify"><button class="small" @click="pickUpdateTarget(item)">选中</button></td>
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
  padding: 20px 24px;
  color: #1f2937;
  min-height: 100vh;
  box-sizing: border-box;
}

.page-header {
  margin-bottom: 16px;
  width: 100%;
}

.page-header h1 {
  margin: 0;
  font-size: 26px;
}

.page-header p {
  margin: 6px 0 0;
  color: #4b5563;
}

.message {
  color: #0f766e;
  margin: 8px 0 16px;
}

.card {
  background: #fff;
  border: 1px solid #dbe4f0;
  border-radius: 12px;
  padding: 18px;
  margin-bottom: 18px;
  width: 100%;
  box-sizing: border-box;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.grid.compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.page :is(input:not([type='checkbox']), select, textarea) {
  border: 2px solid #93c5fd !important;
  border-radius: 10px !important;
  padding: 0 12px !important;
  font-size: 14px;
  background: #eff6ff !important;
  color: #111827 !important;
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
  border-color: #2563eb !important;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15) !important;
}

.page input[readonly] {
  background: #f8fafc !important;
  border-color: #cbd5e1 !important;
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
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.relation-picker {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.chips {
  margin-top: 6px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #93c5fd;
  background: #dbeafe;
  color: #1e3a8a;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  line-height: 1;
}

.chip-remove {
  border: 0;
  background: transparent;
  color: #1e3a8a;
  font-size: 14px;
  line-height: 1;
  padding: 0;
  cursor: pointer;
}

button {
  border: 0;
  border-radius: 8px;
  background: #2563eb;
  color: #fff;
  padding: 8px 14px;
  cursor: pointer;
}

button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

button.small {
  padding: 4px 10px;
}

button.danger {
  background: #dc2626;
}

.option-editor-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 14px 0;
}

.option-editor {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px;
}

.option-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.option-header h3 {
  margin: 0;
  font-size: 14px;
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
  font-weight: 600;
}

.hint {
  color: #6b7280;
  margin-top: 8px;
}

table {
  width: 100%;
  border-collapse: collapse;
}

.table-wrap {
  width: 100%;
  overflow-x: auto;
}

th,
td {
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
  padding: 10px 6px;
  font-size: 13px;
}

@media (max-width: 1000px) {
  .grid,
  .grid.compact,
  .option-editor-wrap {
    grid-template-columns: 1fr;
  }

  .option-row {
    grid-template-columns: 1fr;
  }
}
</style>
