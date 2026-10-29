// Pictures that stand in for the screenshots and photos a real phone holds.
// Each fills its box, cropped from the top as the app crops (design.md §6).

import { useId, type ReactNode } from 'react'

export type ArtName = 'posting' | 'offer' | 'ramen' | 'ticket' | 'sunset' | 'map' | 'tweet' | 'chat'

const SANS = 'Instrument Sans Variable, system-ui, sans-serif'

export function Art({ name }: { name: ArtName }) {
  const Picture = ART[name]
  return <Picture />
}

function Frame({ height, children, fill }: { height: number; children: ReactNode; fill: string }) {
  return (
    <svg viewBox={`0 0 200 ${height}`} preserveAspectRatio="xMidYMin slice" className="block h-full w-full" aria-hidden="true">
      <rect width="200" height={height} fill={fill} />
      {children}
    </svg>
  )
}

const Lines = ({ x, y, widths, gap = 9, h = 4, fill }: { x: number; y: number; widths: number[]; gap?: number; h?: number; fill: string }) => (
  <>
    {widths.map((w, i) => (
      <rect key={i} x={x} y={y + i * gap} width={w} height={h} rx={h / 2} fill={fill} />
    ))}
  </>
)

const ART: Record<ArtName, () => ReactNode> = {
  // An internship posting, screenshotted from a jobs app.
  posting: () => (
    <Frame height={260} fill="#f3f2ef">
      <rect width="200" height="26" fill="#ffffff" />
      <rect x="10" y="8" width="11" height="11" rx="2" fill="#0a66c2" />
      <rect x="28" y="10" width="70" height="7" rx="3.5" fill="#eef3f8" />
      <rect x="8" y="34" width="184" height="216" rx="6" fill="#ffffff" />
      <rect x="18" y="46" width="30" height="30" rx="4" fill="#1f3b57" />
      <circle cx="33" cy="61" r="7" fill="#7fb3e6" />
      <Lines x={56} y={50} widths={[90, 60]} gap={11} h={5} fill="#38434f" />
      <text x="18" y="100" fontFamily={SANS} fontSize="13" fontWeight="650" fill="#1d2226">
        Summer research
      </text>
      <text x="18" y="117" fontFamily={SANS} fontSize="13" fontWeight="650" fill="#1d2226">
        internship 2026
      </text>
      <Lines x={18} y={130} widths={[150, 120, 160, 96]} fill="#a3abb3" />
      <rect x="18" y="176" width="70" height="20" rx="10" fill="#0a66c2" />
      <rect x="31" y="183" width="44" height="6" rx="3" fill="#ffffff" />
      <rect x="94" y="176" width="54" height="20" rx="10" fill="none" stroke="#0a66c2" strokeWidth="1.4" />
      <Lines x={18} y={210} widths={[160, 140, 150]} fill="#c9cfd5" />
    </Frame>
  ),

  // An offer letter, screenshotted from email.
  offer: () => (
    <Frame height={220} fill="#ffffff">
      <rect width="200" height="24" fill="#f6f8fc" />
      <rect x="10" y="9" width="16" height="6" rx="3" fill="#c5d0e0" />
      <text x="14" y="48" fontFamily={SANS} fontSize="12" fontWeight="650" fill="#1f1f1f">
        Your offer: Software
      </text>
      <text x="14" y="63" fontFamily={SANS} fontSize="12" fontWeight="650" fill="#1f1f1f">
        Engineering Intern
      </text>
      <circle cx="22" cy="84" r="8" fill="#c5221f" />
      <Lines x={36} y={79} widths={[70, 50]} gap={8} h={4} fill="#5f6368" />
      <Lines x={14} y={106} widths={[168, 150, 172, 120]} gap={10} fill="#bdc1c6" />
      <rect x="14" y="150" width="172" height="40" rx="5" fill="#e8f0fe" />
      <Lines x={24} y={160} widths={[110, 80]} gap={11} h={5} fill="#1a73e8" />
      <Lines x={14} y={200} widths={[150]} fill="#bdc1c6" />
    </Frame>
  ),

  // Ramen from above: a photo with no words in it.
  ramen: () => (
    <Frame height={200} fill="#3a2a1f">
      <rect y="150" width="200" height="50" fill="#2e2118" />
      <circle cx="100" cy="100" r="82" fill="#e9dccb" />
      <circle cx="100" cy="100" r="68" fill="#b8661c" />
      <path
        d="M48 92c14-10 24 10 38 0s24 10 38 0 24 10 30 4M48 112c14-10 24 10 38 0s24 10 38 0 24 10 30 4M54 130c12-8 20 8 32 0s20 8 32 0 20 8 28 2"
        stroke="#f1d58f"
        strokeWidth="5"
        fill="none"
        strokeLinecap="round"
      />
      <ellipse cx="126" cy="70" rx="21" ry="17" fill="#fbf5ea" />
      <circle cx="128" cy="71" r="9" fill="#f2a334" />
      <rect x="62" y="54" width="34" height="20" rx="3" fill="#6d3d1d" transform="rotate(-14 79 64)" />
      <rect x="66" y="58" width="26" height="4" rx="2" fill="#8d5530" transform="rotate(-14 79 64)" />
      {[
        [66, 104],
        [140, 118],
        [92, 142],
        [120, 96],
        [80, 82],
      ].map(([x, y], i) => (
        <circle key={i} cx={x} cy={y} r="4" fill="#7c9a3f" />
      ))}
      <rect x="150" y="10" width="8" height="140" rx="4" fill="#c49a6c" transform="rotate(18 154 80)" />
      <rect x="164" y="12" width="8" height="140" rx="4" fill="#b5895c" transform="rotate(18 168 82)" />
    </Frame>
  ),

  // A boarding pass, screenshotted from an airline's email.
  ticket: () => (
    <Frame height={190} fill="#eef1f5">
      <rect x="10" y="12" width="180" height="166" rx="10" fill="#ffffff" />
      <rect x="10" y="12" width="180" height="40" rx="10" fill="#1f3a6e" />
      <rect x="10" y="40" width="180" height="12" fill="#1f3a6e" />
      <rect x="22" y="26" width="50" height="7" rx="3.5" fill="#9fb4dc" />
      <text x="22" y="86" fontFamily={SANS} fontSize="24" fontWeight="700" fill="#1f3a6e">
        BLR
      </text>
      <text x="130" y="86" fontFamily={SANS} fontSize="24" fontWeight="700" fill="#1f3a6e">
        CCU
      </text>
      <path d="M78 78h44" stroke="#9fb4dc" strokeWidth="2" strokeDasharray="3 4" />
      <path d="M96 72l9 6-9 6" fill="none" stroke="#1f3a6e" strokeWidth="2" strokeLinecap="round" />
      <Lines x={22} y={100} widths={[40, 30, 46]} gap={0} fill="#c3cad6" />
      <Lines x={22} y={112} widths={[150]} fill="#e3e7ee" />
      <rect x="22" y="128" width="156" height="36" rx="4" fill="#f4f6fa" />
      {Array.from({ length: 30 }, (_, i) => (
        <rect key={i} x={28 + i * 5} y="134" width={i % 3 === 0 ? 3 : 1.6} height="24" fill="#1f2937" />
      ))}
    </Frame>
  ),

  // A sunset over the sea, from the camera.
  sunset: function Sunset() {
    // Many copies share a page, so each names its own gradients.
    const id = useId().replace(/[^a-zA-Z0-9_-]/g, '')
    return (
    <svg viewBox="0 0 200 230" preserveAspectRatio="xMidYMin slice" className="block h-full w-full" aria-hidden="true">
      <defs>
        <linearGradient id={`${id}sky`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#2a2340" />
          <stop offset="0.45" stopColor="#8a4a62" />
          <stop offset="0.75" stopColor="#e2725b" />
          <stop offset="1" stopColor="#f6a35c" />
        </linearGradient>
        <linearGradient id={`${id}sea`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#5b3a4e" />
          <stop offset="1" stopColor="#1d1626" />
        </linearGradient>
      </defs>
      <rect width="200" height="150" fill={`url(#${id}sky)`} />
      <circle cx="104" cy="140" r="26" fill="#ffd59a" />
      <rect y="148" width="200" height="82" fill={`url(#${id}sea)`} />
      {[0, 1, 2, 3, 4, 5].map((i) => (
        <rect key={i} x={84 - i * 3} y={156 + i * 11} width={40 + i * 6} height="2.5" rx="1.25" fill="#ffd59a" opacity={0.7 - i * 0.1} />
      ))}
      <path d="M0 150c30-8 50-4 70 0" stroke="#3b2638" strokeWidth="6" />
    </svg>
    )
  },

  // A pin in a maps app.
  map: () => (
    <Frame height={210} fill="#e8eef1">
      <rect x="120" y="0" width="80" height="70" fill="#c8e6c9" />
      <path d="M0 150c40-10 70 20 110 6s60-30 90-20v80H0z" fill="#aadaff" />
      <path d="M-5 60h210M60 -5v220M-5 118l210-40M150 -5l-30 220" stroke="#ffffff" strokeWidth="8" />
      <path d="M-5 60h210M60 -5v220" stroke="#fde293" strokeWidth="3" />
      <path d="M96 52c0-12 9-20 20-20s20 8 20 20c0 15-20 34-20 34S96 67 96 52z" fill="#e94235" />
      <circle cx="116" cy="52" r="7" fill="#ffffff" />
      <rect x="10" y="150" width="180" height="52" rx="8" fill="#ffffff" />
      <Lines x={22} y={162} widths={[110, 70]} gap={11} h={6} fill="#3c4043" />
      <rect x="22" y="186" width="40" height="8" rx="4" fill="#1a73e8" />
    </Frame>
  ),

  // A post, screenshotted from X.
  tweet: () => (
    <Frame height={180} fill="#000000">
      <circle cx="24" cy="26" r="12" fill="#3d4a5c" />
      <Lines x={44} y={18} widths={[60, 40]} gap={10} h={5} fill="#71767b" />
      <text x="14" y="62" fontFamily={SANS} fontSize="12.5" fill="#e7e9ea">
        the best way to learn a
      </text>
      <text x="14" y="79" fontFamily={SANS} fontSize="12.5" fill="#e7e9ea">
        codebase is to break it
      </text>
      <text x="14" y="96" fontFamily={SANS} fontSize="12.5" fill="#e7e9ea">
        on purpose
      </text>
      <Lines x={14} y={114} widths={[80]} fill="#3e4144" />
      <path d="M0 130h200" stroke="#2f3336" />
      {[20, 70, 120, 170].map((x) => (
        <g key={x}>
          <circle cx={x} cy="150" r="5" fill="none" stroke="#71767b" strokeWidth="1.4" />
          <rect x={x + 9} y="148" width="14" height="4" rx="2" fill="#71767b" />
        </g>
      ))}
    </Frame>
  ),

  // A chat, with a link someone sent.
  chat: () => (
    <Frame height={240} fill="#0b141a">
      <rect width="200" height="28" fill="#1f2c34" />
      <circle cx="18" cy="14" r="8" fill="#6a7f8a" />
      <rect x="32" y="10" width="54" height="7" rx="3.5" fill="#d1d7db" />
      <rect x="10" y="40" width="120" height="30" rx="7" fill="#202c33" />
      <Lines x={18} y={48} widths={[96, 70]} gap={9} fill="#8696a0" />
      <rect x="62" y="80" width="128" height="94" rx="7" fill="#005c4b" />
      <rect x="68" y="86" width="116" height="52" rx="4" fill="#025144" />
      <rect x="68" y="86" width="40" height="52" rx="4" fill="#b8661c" />
      <circle cx="88" cy="112" r="12" fill="#e9dccb" />
      <circle cx="88" cy="112" r="9" fill="#d2843a" />
      <Lines x={114} y={94} widths={[60, 44, 52]} gap={9} fill="#9fd8c9" />
      <Lines x={70} y={148} widths={[100, 70]} gap={9} fill="#d1f4cc" />
      <rect x="10" y="184" width="104" height="22" rx="7" fill="#202c33" />
      <Lines x={18} y={193} widths={[80]} fill="#8696a0" />
    </Frame>
  ),
}
