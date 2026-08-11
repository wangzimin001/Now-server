import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const serverRoot = path.resolve(scriptDir, '..')
const datasetRoot = process.argv[2]
  ? path.resolve(process.argv[2])
  : path.resolve(serverRoot, '..', 'exercises-dataset-main')
const sourceFile = path.join(datasetRoot, 'data', 'exercises.json')
const outputFile = path.join(serverRoot, 'src', 'main', 'resources', 'db', 'migration', 'V4__exercise_dataset.sql')

const CATEGORY_META = {
  chest: { name: '胸部', sort: 10 },
  back: { name: '背部', sort: 20 },
  shoulders: { name: '肩部', sort: 30 },
  'upper legs': { name: '大腿', sort: 40 },
  'upper arms': { name: '上臂', sort: 50 },
  waist: { name: '腰腹', sort: 60 },
  'lower legs': { name: '小腿', sort: 70 },
  'lower arms': { name: '前臂', sort: 80 },
  cardio: { name: '有氧', sort: 90 },
  neck: { name: '颈部', sort: 100 }
}

const EQUIPMENT_MAP = {
  'body weight': '自重',
  dumbbell: '哑铃',
  cable: '绳索器械',
  barbell: '杠铃',
  'leverage machine': '固定器械',
  band: '弹力带',
  'smith machine': '史密斯机',
  kettlebell: '壶铃',
  weighted: '负重',
  'stability ball': '瑞士球',
  'ez barbell': 'EZ杠',
  'sled machine': '雪橇机',
  assisted: '辅助器械',
  'medicine ball': '药球',
  rope: '绳索',
  roller: '泡沫轴',
  'resistance band': '阻力带',
  'bosu ball': 'BOSU球',
  'wheel roller': '健腹轮',
  'olympic barbell': '奥林匹克杠铃',
  tire: '轮胎',
  'trap bar': '六角杠铃',
  'stepmill machine': '登阶机',
  'elliptical machine': '椭圆机',
  hammer: '锤铃',
  'skierg machine': '滑雪机',
  'stationary bike': '健身车',
  'upper body ergometer': '上肢功率车'
}

const MUSCLE_MAP = {
  shoulders: '肩部', forearms: '前臂', biceps: '肱二头肌', triceps: '肱三头肌',
  hamstrings: '腘绳肌', quadriceps: '股四头肌', glutes: '臀肌', obliques: '腹斜肌',
  'hip flexors': '髋屈肌', chest: '胸肌', trapezius: '斜方肌', traps: '斜方肌',
  deltoids: '三角肌', calves: '小腿肌群', ankles: '踝部肌群', core: '核心肌群',
  'lower back': '下背部', soleus: '比目鱼肌', 'rotator cuff': '肩袖肌群',
  'wrist flexors': '腕屈肌', 'latissimus dorsi': '背阔肌', rhomboids: '菱形肌',
  abdominals: '腹肌', wrists: '手腕', hands: '手部', lats: '背阔肌',
  'ankle stabilizers': '踝关节稳定肌', 'upper back': '上背部', 'wrist extensors': '腕伸肌'
}

const TARGET_MAP = {
  abs: '腹肌', pectorals: '胸肌', biceps: '肱二头肌', glutes: '臀肌', delts: '三角肌',
  triceps: '肱三头肌', 'upper back': '上背部', lats: '背阔肌', calves: '小腿肌群',
  quads: '股四头肌', forearms: '前臂肌群', 'cardiovascular system': '心肺系统',
  hamstrings: '腘绳肌', spine: '竖脊肌', traps: '斜方肌', adductors: '内收肌群',
  abductors: '外展肌群', 'serratus anterior': '前锯肌', 'levator scapulae': '肩胛提肌'
}

const EXACT_NAME_MAP = {
  '3/4 sit-up': '四分之三仰卧起坐',
  '45° side bend': '45度侧屈',
  'air bike': '空中自行车卷腹',
  'archer pull up': '弓箭手引体向上',
  'archer push up': '弓箭手俯卧撑',
  'barbell bench press': '杠铃卧推',
  'barbell deadlift': '杠铃硬拉',
  'barbell front squat': '杠铃前蹲',
  'barbell full squat': '杠铃深蹲',
  'barbell incline bench press': '上斜杠铃卧推',
  'barbell romanian deadlift': '杠铃罗马尼亚硬拉',
  'cable seated row': '坐姿绳索划船',
  'dumbbell bench press': '哑铃卧推',
  'dumbbell biceps curl': '哑铃弯举',
  'dumbbell lateral raise': '哑铃侧平举',
  'dumbbell romanian deadlift': '哑铃罗马尼亚硬拉',
  'pull-up': '引体向上',
  'push-up': '俯卧撑'
}

const PHRASE_MAP = {
  'assisted hanging knee raise with throw down': '辅助悬垂举膝抗阻下压',
  'assisted lying leg raise with lateral throw down': '辅助仰卧侧向抗阻举腿',
  'assisted lying leg raise with throw down': '辅助仰卧抗阻举腿',
  'arms overhead full sit up': '双臂过顶全程仰卧起坐',
  'close grip bench press': '窄握卧推',
  'close grip triceps extension': '窄握肱三头肌臂屈伸',
  'decline close grip to skull press': '下斜窄握仰卧臂屈伸',
  'glute ham raise': '臀腿挺身',
  'lying triceps extension skull crusher': '仰卧肱三头肌臂屈伸',
  'one arm bent over row': '单臂俯身划船',
  'one arm overhead biceps curl': '单臂过顶肱二头肌弯举',
  'one arm single leg split squat': '单臂负重单腿分腿蹲',
  'one arm standing low row': '单臂站姿低位划船',
  'one arm twisting chest press': '单臂转体胸推',
  'one arm twisting seated row': '单臂转体坐姿划船',
  'palms down wrist curl': '反握腕弯举',
  'palms up wrist curl': '正握腕弯举',
  'pike to cobra': '折刀式转眼镜蛇式',
  'reverse grip bench press': '反握卧推',
  'reverse wrist curl': '反向腕弯举',
  'straight back stiff leg deadlift': '直背直腿硬拉',
  'straight leg deadlift': '直腿硬拉',
  'wide grip bench press': '宽握卧推',
  'wide grip chest dip': '宽握双杠臂屈伸',
  'alternating biceps curl': '交替弯举',
  'arnold press': '阿诺德推举',
  'back extension': '山羊挺身',
  'battle rope': '战绳',
  'battling ropes': '战绳',
  'bench press': '卧推',
  'bent arm pullover': '屈臂上拉',
  'bent over row': '俯身划船',
  'biceps curl': '肱二头肌弯举',
  'bicycle crunch': '自行车卷腹',
  'bottoms up': '倒置',
  'box squat': '箱式深蹲',
  'bulgarian split squat': '保加利亚分腿蹲',
  'calf raise': '提踵',
  'chest dip': '双杠臂屈伸',
  'chest fly': '夹胸飞鸟',
  'chest press': '胸推',
  'chin up': '反手引体向上',
  'concentration curl': '集中弯举',
  'cossack squat': '哥萨克深蹲',
  'cross body': '跨体',
  'dead bug': '死虫式',
  'decline bench press': '下斜卧推',
  'diamond push up': '钻石俯卧撑',
  'donkey calf raise': '骑驴式提踵',
  'face pull': '面拉',
  'front squat': '前蹲',
  'goblet squat': '高脚杯深蹲',
  'good morning': '早安式',
  'glute bridge': '臀桥',
  'hack squat': '哈克深蹲',
  'hammer curl': '锤式弯举',
  'handstand push up': '倒立俯卧撑',
  'hip abduction': '髋外展',
  'hip adduction': '髋内收',
  'hip extension': '伸髋',
  'hip internal rotation': '髋内旋',
  'hip thrust': '臀推',
  'incline bench press': '上斜卧推',
  'incline row': '上斜划船',
  'jack knife sit up': '折刀仰卧起坐',
  'jump squat': '跳跃深蹲',
  'knee raise': '举膝',
  'lat pulldown': '高位下拉',
  'lateral lunge': '侧弓步',
  'lateral raise': '侧平举',
  'leg curl': '腿弯举',
  'leg extension': '腿屈伸',
  'leg press': '腿举',
  'leg raise': '举腿',
  'military press': '军事推举',
  'mountain climber': '登山跑',
  'narrow stance squat': '窄距深蹲',
  'overhead press': '过顶推举',
  'overhead squat': '过顶深蹲',
  'pallof press': '帕洛夫抗旋推',
  'pendlay row': '彭德雷划船',
  'preacher curl': '牧师凳弯举',
  'pull through': '绳索胯下拉',
  'pull up': '引体向上',
  'push down': '下压',
  'push up': '俯卧撑',
  'rear delt': '三角肌后束',
  'reverse fly': '反向飞鸟',
  'romanian deadlift': '罗马尼亚硬拉',
  'russian twist': '俄罗斯转体',
  'shoulder press': '肩上推举',
  'side plank': '侧平板支撑',
  'sissy squat': '西西深蹲',
  'skull crusher': '仰卧臂屈伸',
  'split squat': '分腿蹲',
  'stiff leg deadlift': '直腿硬拉',
  'sumo deadlift': '相扑硬拉',
  't bar row': 'T杠划船',
  'triceps dip': '肱三头肌臂屈伸',
  'triceps extension': '肱三头肌臂屈伸',
  'upright row': '直立划船',
  'v up': 'V字卷腹',
  'walking lunge': '行走弓步',
  'wall sit': '靠墙静蹲',
  'wrist curl': '腕弯举',
  'zercher squat': '泽奇深蹲',
  'zottman curl': '佐特曼弯举'
}

const WORD_MAP = {
  dumbbell: '哑铃', dumbbells: '哑铃', barbell: '杠铃', cable: '绳索', band: '弹力带',
  kettlebell: '壶铃', lever: '器械', machine: '器械', smith: '史密斯机', weighted: '负重',
  bodyweight: '自重', ball: '球', exercise: '健身', stability: '瑞士', medicine: '药',
  ez: 'EZ杠', 'ez-bar': 'EZ杠', 'ez-barbell': 'EZ杠', 'sz-bar': '曲杆', rope: '绳索',
  roller: '滚轮', wheel: '健腹轮', sled: '雪橇机', bosu: 'BOSU', olympic: '奥林匹克',
  tire: '轮胎', trap: '六角杠', stepmill: '登阶机', elliptical: '椭圆机', skierg: '滑雪机',
  bike: '自行车', ergometer: '功率车', treadmill: '跑步机', gripper: '握力器',
  seated: '坐姿', standing: '站姿', lying: '仰卧', prone: '俯卧', supine: '仰卧', kneeling: '跪姿',
  incline: '上斜', decline: '下斜', floor: '地板', bench: '训练凳', wall: '靠墙', chair: '椅上',
  hanging: '悬垂', suspended: '悬吊', supported: '支撑', assisted: '辅助', fixed: '固定',
  reverse: '反向', reversed: '反向', inverse: '反向', alternating: '交替', alternate: '交替',
  unilateral: '单侧', one: '单', two: '双', three: '三', single: '单侧', double: '双侧',
  arm: '臂', arms: '双臂', leg: '腿', legs: '双腿', knee: '膝', knees: '双膝',
  hand: '手', hands: '双手', finger: '手指', wrist: '手腕', wrists: '双腕', elbow: '肘',
  ankle: '踝关节', ankles: '双踝', heel: '脚跟', feet: '双脚', toe: '脚尖',
  close: '窄距', wide: '宽距', narrow: '窄距', grip: '握', gripless: '无握把',
  overhand: '正握', underhand: '反握', pronated: '正握', supinated: '反握', neutral: '中立握',
  'close-grip': '窄握', 'wide-grip': '宽握', 'reverse-grip': '反握', 'clean-grip': '翻举握法',
  'pronate-grip': '正握', 'palm-in': '掌心相对', palms: '掌心', palm: '掌心',
  front: '前侧', rear: '后侧', back: '后侧', side: '侧向', lateral: '侧向', horizontal: '水平',
  vertical: '垂直', diagonal: '对角', inner: '内侧', outer: '外侧', inside: '内侧', outside: '外侧',
  upper: '上部', lower: '下部', overhead: '过顶', low: '低位', high: '高位',
  straight: '直', bent: '屈曲', 'bent-over': '俯身', stiff: '直', extended: '伸展', elevated: '抬高',
  full: '全程', half: '半程', quarter: '四分之一程', deep: '深幅', short: '短程',
  curl: '弯举', curls: '弯举', press: '推举', presses: '推举', raise: '上举', raises: '上举',
  row: '划船', extension: '伸展', flexion: '屈曲', pulldown: '下拉', pullover: '上拉',
  squat: '深蹲', squats: '深蹲', lunge: '弓步', deadlift: '硬拉', fly: '飞鸟', flyes: '飞鸟',
  crunch: '卷腹', crunches: '卷腹', 'sit-up': '仰卧起坐', sit: '坐', plank: '平板支撑',
  dip: '臂屈伸', dips: '臂屈伸', pushdown: '下压', push: '推', pull: '拉',
  'pull-up': '引体向上', 'pull-ups': '引体向上', 'chin-up': '反手引体向上', 'chin-ups': '反手引体向上',
  'push-up': '俯卧撑', 'muscle-up': '双力臂', 'l-pull-up': 'L式引体向上',
  kickback: '后踢', kickbacks: '后踢', shrug: '耸肩', twist: '转体', twists: '转体', twisting: '转体',
  rotation: '旋转', rotational: '旋转', internal: '内旋', external: '外旋', abduction: '外展', adduction: '内收',
  lift: '抬举', lifting: '抬举', bridge: '桥式', stretch: '拉伸', hyperextension: '超伸展',
  jump: '跳跃', jumps: '跳跃', jumping: '跳跃', run: '跑', sprint: '冲刺跑', sprints: '冲刺跑',
  step: '踏步', 'step-up': '登阶', walk: '行走', walking: '行走', swing: '摆动', kick: '踢腿', kicks: '踢腿',
  clean: '翻举', snatch: '抓举', jerk: '挺举', thruster: '深蹲推举', carry: '行走负重',
  circle: '环绕', circles: '环绕', circular: '环绕', touch: '触碰', touchers: '触碰', reach: '伸展触碰',
  hold: '静态保持', isometric: '等长', dynamic: '动态', balance: '平衡', stabilization: '稳定',
  biceps: '肱二头肌', bicep: '肱二头肌', triceps: '肱三头肌', tricep: '肱三头肌',
  chest: '胸部', shoulder: '肩部', delt: '三角肌', deltoid: '三角肌', lat: '背阔肌',
  glute: '臀肌', glutes: '臀肌', gluteus: '臀肌', hamstring: '腘绳肌', quads: '股四头肌', quad: '股四头肌',
  calf: '小腿', calves: '小腿', ab: '腹肌', abdominal: '腹肌', oblique: '腹斜肌',
  pectoralis: '胸肌', pec: '胸肌', major: '大肌', rectus: '直肌', femoris: '股直肌', femoral: '股部',
  piriformis: '梨状肌', adductor: '内收肌', abductor: '外展肌', peroneals: '腓骨肌', tibialis: '胫骨前肌',
  scapula: '肩胛骨', scapular: '肩胛', spine: '脊柱', groin: '腹股沟', sternum: '胸骨', pelvic: '骨盆',
  head: '头后', neck: '颈部', body: '身体', core: '核心', muscle: '肌肉',
  arnold: '阿诺德', zottman: '佐特曼', jefferson: '杰斐逊', zercher: '泽奇', pendlay: '彭德雷',
  jm: 'JM', tate: '泰特', scott: '斯科特', thibaudeau: '蒂博杜', gironda: '吉隆达',
  cuban: '古巴式', pallof: '帕洛夫', cossack: '哥萨克', turkish: '土耳其式', korean: '韩式',
  french: '法式', hindu: '印度式', janda: '扬达', otis: '奥蒂斯', rocky: '洛奇式',
  bradford: '布拉德福德', svend: '斯文德', zottman: '佐特曼', guillotine: '断头台式',
  hack: '哈克', sumo: '相扑式', romanian: '罗马尼亚式', goblet: '高脚杯式', military: '军事式',
  preacher: '牧师凳', spider: '蜘蛛式', concentration: '集中式', hammer: '锤式', pistol: '手枪式',
  sissy: '西西式', prisoner: '囚徒式', renegade: '俯卧式', archer: '弓箭手式',
  good: '早安', morning: '式', russian: '俄罗斯式', donkey: '骑驴式', frog: '青蛙式',
  monster: '怪兽式', superman: '超人式', inchworm: '毛毛虫式', butterfly: '蝴蝶式', judo: '柔道式',
  bear: '熊爬式', crab: '螃蟹式', cat: '猫式', dog: '犬式', skier: '滑雪式', skater: '滑冰式',
  planche: '水平支撑', maltese: '马耳他式', stalder: '斯塔德式', handstand: '倒立', l: 'L式',
  pike: '折刀式', tuck: '团身', straddle: '分腿', astride: '跨立', hollow: '中空式',
  power: '力量式', plyo: '增强式', negative: '离心', explosive: '爆发式', quick: '快速',
  anti: '抗', gravity: '重力', impossible: '高难度', modified: '改良式', basic: '基础式',
  intermediate: '中级', advanced: '高级', pro: '进阶式', variation: '变式', sequence: '组合',
  boxer: '拳击式', boxing: '拳击', bowling: '保龄球式', kayak: '皮划艇式', ski: '滑雪式', yoga: '瑜伽',
  mountain: '登山', climber: '跑', burpee: '波比跳', jack: '开合跳', jumping: '跳跃',
  march: '原地踏步', staircase: '楼梯', stationary: '固定式', cycle: '骑行', swimmer: '游泳式',
  windmill: '风车式', seesaw: '跷跷板式', wind: '绕环', around: '环绕',
  clap: '击掌', clock: '时钟式', star: '星形', figure: '数字', eight: '8字', '8': '8字',
  skull: '颅后', crusher: '臂屈伸', skullcrusher: '仰卧臂屈伸',
  clean: '翻举', 'clean-grip': '翻举握法', rack: '深蹲架', pin: '安全销', board: '木板', box: '跳箱',
  landmine: '地雷管', 't-bar': 'T杠', bar: '杆', 'v-bar': 'V形把手', attachment: '把手', handle: '把手',
  strap: '拉力带', straps: '拉力带', cage: '深蹲架', platform: '平台', pad: '垫上',
  slide: '滑动', rollerout: '滚轮前伸', rollerer: '滚轮', fallout: '前倾伸展', wheel: '健腹轮',
  hyper: '超伸展', rocker: '摇摆', rocking: '摇摆', drive: '蹬伸', thrusts: '推髋',
  skin: '翻转', catch: '接杠', release: '放手', flip: '翻转', pass: '传递', slam: '砸球',
  throw: '抛下抗阻', drop: '下落', depth: '深度', hop: '单脚跳', hops: '跳跃',
  'side-to-side': '左右交替', cross: '交叉', crossover: '交叉', crossovers: '交叉', 'cross-over': '交叉',
  forward: '向前', backward: '向后', 'back-and-forth': '前后', forth: '向前', up: '向上', down: '向下',
  upward: '向上', facing: '面向', apart: '分开', together: '并拢', across: '横向', between: '之间',
  from: '从', into: '转入', against: '抵住', over: '越过', under: '下方', above: '上方',
  left: '左侧', right: '右侧', middle: '中间', position: '姿势', range: '幅度', angle: '角度', angled: '角度式',
  45: '45度', '45°': '45度', '°': '度', 180: '180度', 360: '360度',
  v: 'V字', 'v-up': 'V字卷腹', 'v-sit': 'V字坐姿', 'l-sit': 'L字支撑', 'y-raise': 'Y字上举',
  't-raise': 'T字上举', 'w-press': 'W字推举', 'up-down': '上下式', 'curl-up': '卷腹',
  'pull-in': '屈膝收腹', 'butt-ups': '臀部上抬', 'bottoms-up': '倒置', 'body-up': '身体上撑',
  'body-up': '身体上撑', 'dip-pull-up': '臂屈伸接引体向上', 'pike-to-cobra': '折刀式转眼镜蛇式',
  'elbow-to-knee': '肘触膝', 'leg-hip': '腿髋', 'two-one': '双侧转单侧',
  blaster: '弯举托板', pulley: '滑轮', support: '支撑', retractor: '后缩', depresor: '下压', posterior: '后侧',
  towel: '毛巾', iron: '铁板', gripper: '握力器', sledge: '大锤', ropes: '绳', stirrups: '脚蹬',
  palms: '掌心', clasped: '交握', open: '打开', outstretched: '伸直',
  all: '', fours: '四点支撑', squad: '深蹲', slingers: '摆动', motion: '动态', fixed: '固定',
  close: '窄距', closer: '窄距', mixed: '混合握法', hook: '锁握', multiple: '多次', no: '无',
  style: '式', pose: '姿势', self: '自助', equipment: '器械', response: '反应',
  potty: '如厕式', stork: '鹳式', london: '伦敦式', pirate: '海盗式', caster: '脚轮式',
  sphinx: '斯芬克斯式', spell: '拼字式', greatest: '最大全程', world: '全球式',
  cocoons: '茧式卷腹', curtsey: '屈膝礼弓步', breeding: '扩胸式', captains: '船长椅',
  frankestein: '弗兰肯斯坦式', frankenstein: '弗兰肯斯坦式', hyght: '海特式', keens: '基恩斯式',
  otis: '奥蒂斯式', stalder: '斯塔德式', spell: '拼字式', thibaudeau: '蒂博杜式',
  '3/4': '四分之三', 2: '第二式', 3: '第三式', plus: '加强式',
  '45в°': '45度', bars: '双杠', behind: '颈后', benches: '训练凳之间', bend: '侧屈', bends: '屈膝',
  butt: '臀部上抬', cambered: '弯杆', can: '满杯式', chin: '引体', climb: '攀爬',
  contralateral: '对侧', crawl: '爬行', degrees: '度', drag: '拖拽式', elevator: '电梯式',
  face: '面部', farmers: '农夫', flag: '旗式', flat: '平凳', flexor: '屈肌', flutter: '交替摆腿',
  gorilla: '大猩猩式', ground: '地面', hang: '悬挂', hip: '髋部', hug: '抱球', inverted: '反向',
  jackknife: '折刀式', kipping: '摆动借力', lean: '前倾', legged: '腿', parallel: '平行握',
  peacher: '牧师凳', point: '点支撑', pronate: '旋前', pronation: '前臂旋前', pyramid: '金字塔式',
  raised: '抬高', reclining: '仰卧', resistance: '阻力带', revers: '反向', ring: '吊环', rotary: '旋转式',
  rotate: '旋转', runners: '跑者', saw: '锯式', scissor: '剪刀跳', sitted: '坐姿', speed: '快速',
  split: '分腿', squatting: '深蹲', squeeze: '挤压', stance: '站姿', stepbox: '登阶箱', stride: '步幅',
  supination: '前臂旋后', supper: '抬腿', sz: '曲杆', t: 'T字', tap: '触碰', tennis: '网球',
  through: '穿过', tilt: '骨盆倾斜', trainer: '训练机', twin: '双把手', twisted: '扭转', upright: '直立',
  ups: '上撑', w: 'W字', waiter: '托举式', wipers: '雨刷式', y: 'Y字',
  male: '男', female: '女', pov: '视角', version: '版本', v2: '第二式', reps: '重复',
  on: '', with: '', to: '', the: '', and: '', of: '', in: '', a: '', both: '双侧', for: '',
  off: '离地', out: '向外', get: '起身', semi: '半', round: '绕环', big: '大幅', quick: '快速'
}

const POPULARITY_PATTERNS = {
  chest: ['bench press', 'push-up', 'chest press', 'chest fly', 'dip', 'pullover'],
  back: ['pull-up', 'pulldown', 'bent over row', 'seated row', 't-bar row', 'one arm row', 'back extension', 'shrug'],
  shoulders: ['shoulder press', 'overhead press', 'lateral raise', 'front raise', 'reverse fly', 'rear delt', 'upright row', 'face pull'],
  'upper legs': ['squat', 'deadlift', 'romanian deadlift', 'leg press', 'lunge', 'leg extension', 'leg curl', 'hip thrust', 'glute bridge'],
  'upper arms': ['biceps curl', 'curl', 'hammer curl', 'preacher curl', 'pushdown', 'triceps extension', 'dip', 'kickback'],
  waist: ['plank', 'crunch', 'sit-up', 'leg raise', 'knee raise', 'russian twist', 'v-up', 'wheel'],
  'lower legs': ['calf raise', 'reverse calf raise', 'ankle'],
  'lower arms': ['wrist curl', 'reverse wrist curl', 'gripper', 'pronation', 'supination'],
  cardio: ['treadmill', 'stationary bike', 'elliptical', 'burpee', 'mountain climber', 'jumping jack', 'run', 'jump'],
  neck: ['neck flexion', 'neck extension']
}

const EQUIPMENT_PRIORITY = {
  'body weight': 0, barbell: 1, dumbbell: 2, cable: 3, 'leverage machine': 4,
  'smith machine': 5, band: 6, kettlebell: 7
}

function normalizeKey(value) {
  return String(value || '').trim().toLowerCase()
}

function tokenizeName(value) {
  return normalizeKey(value)
    .replace(/\((male|female)\)/g, ' $1 ')
    .replace(/\((back pov|side pov)\)/g, ' $1 ')
    .replace(/v\.\s*(\d+)/g, ' version $1 ')
    .replace(/[()]/g, ' ')
    .replace(/[,_.,]/g, ' ')
    .replace(/[–—]/g, '-')
    .replace(/-/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .split(' ')
    .filter(Boolean)
}

const PHRASES = Object.entries(PHRASE_MAP)
  .map(([source, translated]) => [source.replace(/-/g, ' ').split(' '), translated])
  .sort((a, b) => b[0].length - a[0].length)

function translateName(name) {
  const normalized = normalizeKey(name)
  if (EXACT_NAME_MAP[normalized]) return EXACT_NAME_MAP[normalized]

  const tokens = tokenizeName(name)
  const result = []
  const unknown = []

  for (let index = 0; index < tokens.length;) {
    const phrase = PHRASES.find(([parts]) => parts.every((part, offset) => tokens[index + offset] === part))
    if (phrase) {
      result.push(phrase[1])
      index += phrase[0].length
      continue
    }

    const token = tokens[index]
    if (Object.hasOwn(WORD_MAP, token)) {
      if (WORD_MAP[token]) result.push(WORD_MAP[token])
    } else if (/^\d+$/.test(token)) {
      result.push(`第${token}式`)
    } else {
      unknown.push(token)
      result.push(token.toUpperCase())
    }
    index += 1
  }

  const translated = result.join('')
    .replace(/杠铃杠铃/g, '杠铃')
    .replace(/哑铃哑铃/g, '哑铃')
    .replace(/训练凳卧推/g, '卧推')
    .replace(/深蹲深蹲/g, '深蹲')
    .replace(/弯举弯举/g, '弯举')
    .replace(/推举推举/g, '推举')
    .replace(/男$/, '（男）')
    .replace(/女$/, '（女）')

  return { translated: translated || name, unknown }
}

function translateLookup(value, ...maps) {
  const key = normalizeKey(value)
  for (const map of maps) {
    if (map[key]) return map[key]
  }
  const translated = translateName(key)
  return typeof translated === 'string' ? translated : translated.translated
}

function normalizeInstruction(value) {
  return String(value || '')
    .replace(/\s+/g, ' ')
    .replace(/\s+([，。；：！？])/g, '$1')
    .replace(/([，。；：！？])\s+/g, '$1')
    .trim()
}

function popularityRank(exercise, sourceIndex) {
  const name = normalizeKey(exercise.name)
  const patterns = POPULARITY_PATTERNS[normalizeKey(exercise.category)] || []
  let movementRank = 900
  patterns.forEach((pattern, index) => {
    if (name.includes(pattern)) movementRank = Math.min(movementRank, index * 80)
  })
  const equipmentRank = EQUIPMENT_PRIORITY[normalizeKey(exercise.equipment)] ?? 9
  const complexityPenalty = Math.min(name.split(' ').length, 20)
  return movementRank * 1000 + equipmentRank * 100 + complexityPenalty * 2 + sourceIndex
}

function sqlString(value) {
  if (value === null || value === undefined) return 'NULL'
  return `'${String(value)
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "''")
    .replace(/\u0000/g, '')}'`
}

function buildInsert(exercise, index, translatedName) {
  const categoryCode = normalizeKey(exercise.category)
  const category = CATEGORY_META[categoryCode]
  if (!category) throw new Error(`Unknown category: ${exercise.category}`)

  const instructionZh = normalizeInstruction(exercise.instructions?.zh)
  const instructionEn = normalizeInstruction(exercise.instructions?.en || exercise.instruction_steps?.join(' '))
  const secondaryMuscles = Array.isArray(exercise.secondary_muscles)
    ? exercise.secondary_muscles.map(item => translateLookup(item, MUSCLE_MAP, TARGET_MAP))
    : []
  const databaseId = 100000 + Number.parseInt(exercise.id, 10)
  const gifFile = path.basename(exercise.gif_url || '')
  const imageFile = path.basename(exercise.image || '')

  const values = [
    databaseId,
    translatedName,
    translateLookup(exercise.muscle_group, MUSCLE_MAP, TARGET_MAP),
    translateLookup(exercise.equipment, EQUIPMENT_MAP),
    '未分级',
    instructionZh || instructionEn,
    'exercise-dataset',
    exercise.id,
    exercise.name,
    categoryCode,
    category.name,
    category.sort,
    normalizeKey(exercise.equipment),
    normalizeKey(exercise.muscle_group),
    normalizeKey(exercise.target),
    translateLookup(exercise.target, TARGET_MAP, MUSCLE_MAP),
    JSON.stringify(secondaryMuscles),
    instructionEn,
    imageFile ? `/static/exercises/images/${imageFile}` : null,
    gifFile ? `/static/exercises/gifs/${gifFile}` : null,
    exercise.attribution || '© Gym visual — https://gymvisual.com/',
    popularityRank(exercise, index)
  ].map(sqlString).join(', ')

  return `(${values})`
}

const exercises = JSON.parse(fs.readFileSync(sourceFile, 'utf8'))
const unknownTokens = new Map()
const translatedExercises = exercises.map(exercise => {
  const result = translateName(exercise.name)
  if (typeof result === 'string') return { exercise, translatedName: result }
  result.unknown.forEach(token => {
    if (!unknownTokens.has(token)) unknownTokens.set(token, [])
    if (unknownTokens.get(token).length < 4) unknownTokens.get(token).push(exercise.name)
  })
  return { exercise, translatedName: result.translated }
})

const header = `-- Generated from exercises-dataset-main/data/exercises.json.
-- Dataset fields and instruction translations are MIT licensed.
-- Exercise GIF/JPG media remains © Gym visual — https://gymvisual.com/.

INSERT INTO exercise (
    id, name, muscle_group, equipment, level, instructions,
    source, source_exercise_id, name_en, category_code, category_name, category_sort,
    equipment_code, muscle_group_code, target_code, target_name, secondary_muscles,
    instructions_en, image_url, gif_url, attribution, popularity_rank
) VALUES
`

const rows = translatedExercises.map(({ exercise, translatedName }, index) => buildInsert(exercise, index, translatedName))
const footer = `
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    muscle_group = VALUES(muscle_group),
    equipment = VALUES(equipment),
    instructions = VALUES(instructions),
    name_en = VALUES(name_en),
    category_code = VALUES(category_code),
    category_name = VALUES(category_name),
    category_sort = VALUES(category_sort),
    equipment_code = VALUES(equipment_code),
    muscle_group_code = VALUES(muscle_group_code),
    target_code = VALUES(target_code),
    target_name = VALUES(target_name),
    secondary_muscles = VALUES(secondary_muscles),
    instructions_en = VALUES(instructions_en),
    image_url = VALUES(image_url),
    gif_url = VALUES(gif_url),
    attribution = VALUES(attribution),
    popularity_rank = VALUES(popularity_rank);
`

fs.writeFileSync(outputFile, header + rows.join(',\n') + footer, 'utf8')

console.log(`Generated ${rows.length} localized exercises at ${outputFile}`)
console.log(`Unmapped English tokens: ${unknownTokens.size}`)
for (const [token, examples] of [...unknownTokens.entries()].sort((a, b) => a[0].localeCompare(b[0]))) {
  console.log(`  ${token}: ${examples.join(' | ')}`)
}
