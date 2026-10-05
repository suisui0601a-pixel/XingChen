// Read-only audit. Existence/candidate checks are NOT semantic acceptance or a FINAL seal.
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '../..')
function files(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => entry.isDirectory()
    ? files(path.join(dir, entry.name)) : [path.join(dir, entry.name)])
}
const relative = file => path.relative(root, file).replaceAll('\\', '/')
const frontend = files(path.join(root, 'frontend/src')).filter(file => /\.(ts|tsx)$/.test(file) && !/\.test\./.test(file))
const literals = [], notices = [], markers = [], storage = []
for (const file of frontend) {
  const source = fs.readFileSync(file, 'utf8')
  const add = (collection, match, text) => collection.push({ file: relative(file), line: source.slice(0, match.index).split(/\r?\n/).length, text })
  // Deliberately lexical candidate scan, not a claim to identify every computed UI string.
  for (const match of source.matchAll(/<(?:p|small|label|span|h[1-6]|button|a|th|td|option|li|legend|summary|strong|em|caption)\b[^<>]*>([^<>{}\r\n]+)(?=<|{)/g)) {
    const text = match[1].replace(/\s+/g, ' ').trim()
    if (/[a-zA-Z\u4e00-\u9fff]/.test(text)) add(literals, match, text)
  }
  for (const match of source.matchAll(/\b(aria-label|placeholder|title|alt)="([^"]+)"/g)) add(literals, match, `${match[1]}=${match[2]}`)
  for (const match of source.matchAll(/\b(setNotice|setError)\('([^']+)'\)/g)) add(notices, match, match[2])
  source.split(/\r?\n/).forEach((line, index) => {
    if (/\b(TODO|FIXME|HACK|TEMP|XXX)\b/.test(line)) markers.push({ file: relative(file), line: index + 1 })
    if (/localStorage|sessionStorage|dangerouslySetInnerHTML|\.innerHTML/.test(line)) storage.push({ file: relative(file), line: index + 1 })
  })
}
const matrix = fs.readFileSync(path.join(root, 'docs/LEGACY_FEATURE_MATRIX.md'), 'utf8')
const rows = matrix.split('## Evidence index')[0].split(/\r?\n/).filter(line => /^\|\s*\d+[a-z]?\s*\|/.test(line)).map((line, index) => {
  const cells = line.split('|').map(cell => cell.trim())
  return { ordinal: index + 1, id: cells[1], feature: cells[2], status: cells[7].split(/[ (]/)[0], implementation: cells[6], evidence: cells[9] }
})
const counts = Object.fromEntries(['TESTED', 'FOUNDATION', 'PLANNED', 'NEEDS_REVIEW'].map(status => [status, rows.filter(row => row.status === status).length]))
const testFiles = [...files(path.join(root, 'src/test/java')), ...files(path.join(root, 'frontend/src')).filter(file => /\.test\./.test(file)), ...files(path.join(root, 'frontend/e2e'))]
const classes = new Map(testFiles.filter(file => file.endsWith('.java')).map(file => [path.basename(file, '.java'), fs.readFileSync(file, 'utf8')]))
const references = [], fileReferences = []
const classReferences=[], inheritedReferences=[], aliasReferences=[]
const evidenceIndex=matrix.slice(matrix.indexOf('## Evidence index'))
for (const row of rows.filter(row => row.status === 'TESTED')) {
  let inheritedClass
  for(const match of row.evidence.matchAll(/`([^`]+)`/g)){
    const ref=match[1]
    if(classes.has(ref)){classReferences.push({row:row.id,reference:ref,exists:true,tests:(classes.get(ref).match(/@Test\b/g)||[]).length});inheritedClass=ref}
    else if(/^\w+Test\./.test(ref))inheritedClass=ref.split('.')[0]
    else if(/^[a-z]\w+$/.test(ref)&&inheritedClass){
      inheritedReferences.push({row:row.id,reference:inheritedClass+'.'+ref,exists:new RegExp('\\b'+ref+'\\s*\\(').test(classes.get(inheritedClass)||'')})
    }
  }
  for(const match of row.evidence.matchAll(/\b([A-Z]+(?:-[A-Z]+)?)-([0-9]{3})(?:\.\.([0-9]{3}))?/g)){
    const [,prefix,start,end]=match
    const declared=[...evidenceIndex.matchAll(new RegExp(prefix+'-([0-9]{3})(?:\\.\\.([0-9]{3}))?','g'))]
    const numbers=Array.from({length:Number(end||start)-Number(start)+1},(_,i)=>Number(start)+i)
    const indexed=numbers.every(n=>declared.some(d=>n>=Number(d[1])&&n<=Number(d[2]||d[1])))
    const prefixMethods=classes.size && numbers.every(n=>[...classes.values()].some(s=>s.toLowerCase().includes(prefix.toLowerCase().replaceAll('-','')+String(n).padStart(3,'0'))))
    aliasReferences.push({row:row.id,reference:match[0],indexPresent:indexed||prefixMethods,range:!!end})
  }
  for (const match of row.evidence.matchAll(/([A-Z]\w*Test)\.([A-Za-z]\w*(?:\.\.\d+)?)/g)) {
    const [, cls, method] = match, source = classes.get(cls)
    const isRange = method.includes('..')
    const exists = !!source && (isRange ? new RegExp(`\\b${method.split('..')[0]}`).test(source) : new RegExp(`\\b${method}\\s*\\(`).test(source))
    references.push({ row: row.ordinal, reference: `${cls}.${method}`, exists, rangeRequiresSemanticReview: isRange })
  }
  for (const match of row.evidence.matchAll(/\b([A-Za-z][\w.-]*\.(?:test\.tsx|e2e\.spec\.ts))/g)) {
    fileReferences.push({ row: row.ordinal, reference: match[1], exists: testFiles.some(file => path.basename(file) === match[1]) })
  }
}
const dictionaryFile = frontend.find(file => file.endsWith('i18n.ts'))
const indexedMethods=[]
for(const line of evidenceIndex.split(/\r?\n/)){
 let cls
 for(const match of line.matchAll(/`(?:([A-Z]\w*Test))?#([a-z]\w*)`/g)){
  cls=match[1]||cls
  if(cls)indexedMethods.push({reference:cls+'.'+match[2],exists:new RegExp('\\b'+match[2]+'\\s*\\(').test(classes.get(cls)||'')})
 }
}
const dictionarySource = fs.readFileSync(dictionaryFile, 'utf8')
const catalogs = {}
const zhStart = dictionarySource.indexOf("'zh-CN': {"), enStart = dictionarySource.indexOf("'en-US': {")
for (const [language, block] of [['zh-CN', dictionarySource.slice(zhStart, enStart)], ['en-US', dictionarySource.slice(enStart, dictionarySource.indexOf('} as const'))]]) {
  catalogs[language] = [...block.matchAll(/\b([A-Za-z]\w*):\s*['"]/g)].map(match => match[1])
}
const consumerSource = frontend.filter(file => file !== dictionaryFile).map(file => fs.readFileSync(file, 'utf8')).join('\n')
const unusedKeyCandidates = catalogs['zh-CN'].filter(key => !consumerSource.includes(`'${key}'`) && !consumerSource.includes(`"${key}"`))
if (!rows.length || !catalogs['zh-CN'].length || !catalogs['en-US'].length) throw new Error('Audit input did not parse; do not report an empty catalog as passing')
console.log(JSON.stringify({
  note: 'Literal candidates require manual classification; reference existence is not meaningful-test evidence. No runtime or production access.',
  matrix: { rows: rows.length, ...counts, sum: Object.values(counts).reduce((a, b) => a + b, 0), unknownStatuses: rows.filter(row => !Object.hasOwn(counts, row.status)), uniqueIds: new Set(rows.map(row=>row.id)).size, duplicateIds: rows.filter((row, index) => rows.slice(0, index).some(prior => prior.id === row.id)).map(row => row.id),
    qualifiedMethodCount: references.length, missingMethods: references.filter(ref => !ref.exists), rangeReferences: references.filter(ref => ref.rangeRequiresSemanticReview), missingTestFiles: fileReferences.filter(ref => !ref.exists),
    classReferences,inheritedReferences,aliasReferences,indexedMethods,missingIndexedMethods:indexedMethods.filter(r=>!r.exists),
    nonTested: rows.filter(row => row.status !== 'TESTED').map(({ ordinal, id, feature, status }) => ({ ordinal, id, feature, status })) },
  catalogs: { zhKeys: catalogs['zh-CN'].length, enKeys: catalogs['en-US'].length, missingEn: catalogs['zh-CN'].filter(key => !catalogs['en-US'].includes(key)), missingZh: catalogs['en-US'].filter(key => !catalogs['zh-CN'].includes(key)), unusedKeyCandidates },
  untranslatedCandidates: literals, untranslatedNotices: notices, markers, storageAndHtmlLocations: storage,
}, null, 2))
