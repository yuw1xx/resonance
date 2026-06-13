import { motion } from 'framer-motion'
import { Download } from 'lucide-react'

const base = import.meta.env.BASE_URL

const GITHUB_APP = 'https://github.com/yuw1xx/resonance-app'
const GITHUB_RELEASES = 'https://github.com/yuw1xx/resonance-app/releases/latest'

const BADGES = ['Android 15+', 'Kotlin', 'Jetpack Compose', 'Material 3', 'MIT License', 'No Ads']

const containerVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.1 } },
}

const itemVariants = {
  hidden: { opacity: 0, y: 24 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.6, ease: [0.25, 0.46, 0.45, 0.94] } },
}

export default function Hero() {
  return (
    <section className="relative min-h-screen flex items-center justify-center text-center overflow-hidden px-6 pt-16">
      {/* Gradient orbs */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden">
        <div className="absolute top-1/3 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[900px] h-[700px] bg-violet-600/[0.12] rounded-full blur-[140px]" />
        <div className="absolute bottom-1/4 left-1/4 w-[500px] h-[500px] bg-indigo-600/[0.08] rounded-full blur-[120px]" />
        <div className="absolute top-1/3 right-1/4 w-[350px] h-[350px] bg-purple-500/[0.07] rounded-full blur-[100px]" />
      </div>

      <motion.div
        className="relative max-w-3xl"
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        {/* App icon */}
        <motion.div
          variants={{
            hidden: { opacity: 0, scale: 0.8 },
            visible: {
              opacity: 1,
              scale: 1,
              transition: { duration: 0.7, ease: [0.34, 1.56, 0.64, 1] },
            },
          }}
          className="flex justify-center mb-8"
        >
          <img
            src={`${base}icon.png`}
            alt="Resonance"
            className="w-28 h-28 rounded-[28px] animate-float animate-pulse-glow"
          />
        </motion.div>

        {/* Headline */}
        <motion.h1
          variants={itemVariants}
          className="text-[clamp(4rem,12vw,7rem)] font-bold tracking-tight leading-none mb-5"
          style={{
            background: 'linear-gradient(145deg, #ffffff 25%, #C4B5FD 60%, #7C3AED 100%)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
            backgroundClip: 'text',
          }}
        >
          Resonance
        </motion.h1>

        {/* Subtitle */}
        <motion.p variants={itemVariants} className="text-lg font-semibold text-primary mb-4">
          A modern, feature-rich local music player for Android 15+
        </motion.p>

        {/* Description */}
        <motion.p
          variants={itemVariants}
          className="text-[#B0A8C8] text-[1.05rem] max-w-xl mx-auto mb-10 leading-relaxed"
        >
          Gapless playback, ReplayGain, synced lyrics, Last.fm scrobbling, Navidrome streaming,
          Chromecast, and peer-to-peer song sharing. No ads. No subscriptions. Forever free and open source.
        </motion.p>

        {/* CTAs */}
        <motion.div
          variants={itemVariants}
          className="flex flex-wrap gap-3 justify-center mb-10"
        >
          <a
            href={GITHUB_RELEASES}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 bg-primary hover:bg-primary-light text-[#2E1065] font-semibold px-7 py-3.5 rounded-full transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_14px_36px_rgba(167,139,250,0.45)] text-sm"
          >
            <Download size={17} strokeWidth={2.5} />
            Download APK
          </a>
          <a
            href={GITHUB_APP}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 bg-white/[0.07] hover:bg-white/[0.12] border border-white/[0.1] hover:border-white/[0.18] text-[#EDE9F6] font-semibold px-7 py-3.5 rounded-full transition-all duration-200 hover:-translate-y-0.5 text-sm"
          >
            <svg width="17" height="17" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z" />
            </svg>
            View on GitHub
          </a>
        </motion.div>

        {/* Badges */}
        <motion.div variants={itemVariants} className="flex flex-wrap gap-2 justify-center">
          {BADGES.map(badge => (
            <span
              key={badge}
              className="bg-white/[0.05] border border-white/[0.08] text-[#9189A8] text-xs font-medium px-3 py-1.5 rounded-full"
            >
              {badge}
            </span>
          ))}
        </motion.div>
      </motion.div>

      {/* Scroll hint */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 1.2, duration: 0.6 }}
        className="absolute bottom-8 left-1/2 -translate-x-1/2 flex flex-col items-center gap-2"
      >
        <motion.div
          animate={{ y: [0, 6, 0] }}
          transition={{ duration: 1.8, repeat: Infinity, ease: 'easeInOut' }}
          className="w-5 h-8 rounded-full border border-white/[0.15] flex items-start justify-center pt-1.5"
        >
          <div className="w-1 h-1.5 bg-white/30 rounded-full" />
        </motion.div>
      </motion.div>
    </section>
  )
}
