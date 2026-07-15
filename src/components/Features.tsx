import { motion } from 'framer-motion'

const iconProps = {
  width: 21,
  height: 21,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.7,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
}

const icons = {
  playback: (
    <svg {...iconProps} strokeWidth={0}>
      <rect x="2.5" y="9.5" width="2.4" height="6" rx="1.2" fill="currentColor" />
      <rect x="7.3" y="5.5" width="2.4" height="14" rx="1.2" fill="currentColor" />
      <rect x="12.1" y="2" width="2.4" height="21" rx="1.2" fill="currentColor" />
      <rect x="16.9" y="6.5" width="2.4" height="12" rx="1.2" fill="currentColor" />
      <rect x="21.1" y="10.2" width="2.4" height="4.6" rx="1.2" fill="currentColor" />
    </svg>
  ),
  lyrics: (
    <svg {...iconProps}>
      <rect x="9" y="2.5" width="6" height="11" rx="3" />
      <path d="M5.5 11a6.5 6.5 0 0 0 13 0" />
      <path d="M12 17.5V21" />
      <path d="M8.5 21h7" />
    </svg>
  ),
  library: (
    <svg {...iconProps}>
      <path d="M12 3 3 8l9 5 9-5-9-5z" />
      <path d="M3 12l9 5 9-5" />
      <path d="M3 16l9 5 9-5" />
    </svg>
  ),
  share: (
    <svg {...iconProps}>
      <circle cx="18" cy="5" r="2.6" />
      <circle cx="6" cy="12" r="2.6" />
      <circle cx="18" cy="19" r="2.6" />
      <path d="M8.3 10.6 15.7 6.4" />
      <path d="M8.3 13.4l7.4 4.2" />
    </svg>
  ),
  stats: (
    <svg {...iconProps}>
      <path d="M3 17l6-6 4 4 8-8" />
      <path d="M15 6.5h6V12.5" />
    </svg>
  ),
  customize: (
    <svg {...iconProps}>
      <path d="M3 6h18" />
      <path d="M3 12h18" />
      <path d="M3 18h18" />
      <circle cx="8" cy="6" r="2.1" fill="currentColor" stroke="none" />
      <circle cx="16" cy="12" r="2.1" fill="currentColor" stroke="none" />
      <circle cx="10.5" cy="18" r="2.1" fill="currentColor" stroke="none" />
    </svg>
  ),
  stream: (
    <svg {...iconProps}>
      <path d="M2 16.1A5 5 0 0 1 5.9 20" />
      <path d="M2 12.05A9 9 0 0 1 9.95 20" />
      <path d="M2 8V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6" />
      <circle cx="2" cy="20" r="1.3" fill="currentColor" stroke="none" />
    </svg>
  ),
}

const featureGroups = [
  {
    icon: icons.playback,
    title: 'Playback',
    gradient: 'from-violet-500/20 via-violet-500/5 to-transparent',
    iconColor: 'text-violet-300',
    iconBg: 'bg-violet-500/10',
    features: [
      'Gapless playback',
      'Crossfade between songs',
      'ReplayGain 2.0 (per-track & per-album)',
      'Skip silence',
      'Speed & pitch control',
      'Smart shuffle',
      'Sleep timer',
    ],
  },
  {
    icon: icons.lyrics,
    title: 'Lyrics',
    gradient: 'from-blue-500/20 via-blue-500/5 to-transparent',
    iconColor: 'text-blue-300',
    iconBg: 'bg-blue-500/10',
    features: [
      'Synced lyrics (LRC, TTML, karaoke)',
      'Embedded ID3 lyrics',
      'LRCLib auto-fetch',
      'In-app lyrics editor',
    ],
  },
  {
    icon: icons.library,
    title: 'Library',
    gradient: 'from-emerald-500/20 via-emerald-500/5 to-transparent',
    iconColor: 'text-emerald-300',
    iconBg: 'bg-emerald-500/10',
    features: [
      'Full MediaStore integration',
      'Folder browsing',
      'Configurable artist delimiters',
      'Excluded folders',
      'Auto-scan & background sync',
      'Sort & filter by any field',
      'Persistent queue',
    ],
  },
  {
    icon: icons.share,
    title: 'Resonance Share',
    gradient: 'from-cyan-500/20 via-cyan-500/5 to-transparent',
    iconColor: 'text-cyan-300',
    iconBg: 'bg-cyan-500/10',
    features: [
      'Send songs over Wi-Fi or Wi-Fi Direct',
      'Nearby Share via Bluetooth',
      'Long-distance relay, no account needed',
      'QR code & link handoff',
    ],
  },
  {
    icon: icons.stream,
    title: 'Streaming',
    gradient: 'from-sky-500/20 via-sky-500/5 to-transparent',
    iconColor: 'text-sky-300',
    iconBg: 'bg-sky-500/10',
    features: [
      'Navidrome / Subsonic support',
      'Chromecast / Google Cast',
    ],
  },
  {
    icon: icons.stats,
    title: 'Scrobbling & Stats',
    gradient: 'from-rose-500/20 via-rose-500/5 to-transparent',
    iconColor: 'text-rose-300',
    iconBg: 'bg-rose-500/10',
    features: [
      'Last.fm scrobbling',
      'Maloja self-hosted scrobbling',
      'Listening history & play counts',
      'Auto-generated weekly mixes',
      'Play breakdowns by hour & period',
    ],
  },
  {
    icon: icons.customize,
    title: 'UI & Customization',
    gradient: 'from-amber-500/20 via-amber-500/5 to-transparent',
    iconColor: 'text-amber-300',
    iconBg: 'bg-amber-500/10',
    features: [
      'Material 3 with dynamic color',
      'Dark / Light / System theme',
      'Waveform seekbar',
      'Rotating vinyl animation',
      'Multiple player layouts',
      'Blur artwork background',
      'Home screen widget',
      'Tag editor',
    ],
  },
]

export default function Features() {
  return (
    <section id="features" className="py-28 px-6">
      <div className="max-w-6xl mx-auto">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5 }}
          className="text-center mb-16"
        >
          <h2 className="text-4xl md:text-5xl font-bold tracking-tight mb-4">Everything you need</h2>
          <p className="text-[#B0A8C8] text-lg max-w-lg mx-auto">
            Resonance covers every corner of the audiophile experience.
          </p>
        </motion.div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {featureGroups.map((group, i) => (
            <motion.div
              key={group.title}
              initial={{ opacity: 0, y: 32 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-40px' }}
              transition={{ duration: 0.5, delay: i * 0.08 }}
              whileHover={{ y: -4 }}
              className="relative group rounded-2xl border border-white/[0.07] hover:border-white/[0.13] bg-surface/60 backdrop-blur-sm p-6 overflow-hidden transition-colors duration-200"
            >
              {/* Card tint */}
              <div className={`absolute inset-0 bg-gradient-to-br ${group.gradient} opacity-60 pointer-events-none`} />
              {/* Hover glow edge */}
              <div className="absolute inset-0 rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-300 shadow-[inset_0_1px_0_rgba(255,255,255,0.07)]" />

              <div className="relative">
                <div className="flex items-center gap-3 mb-5">
                  <div
                    className={`w-10 h-10 flex items-center justify-center rounded-xl flex-shrink-0 border border-white/[0.06] ${group.iconBg} ${group.iconColor}`}
                  >
                    {group.icon}
                  </div>
                  <h3 className="text-sm font-semibold text-[#EDE9F6]">{group.title}</h3>
                </div>
                <ul className="space-y-2.5">
                  {group.features.map(feature => (
                    <li key={feature} className="flex items-start gap-2.5 text-sm text-[#A89FC0]">
                      <span className="mt-[5px] w-1.5 h-1.5 rounded-full bg-primary/60 flex-shrink-0" />
                      {feature}
                    </li>
                  ))}
                </ul>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}
