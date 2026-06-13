import { motion } from 'framer-motion'

const GITHUB_APP = 'https://github.com/yuw1xx/resonance-app'

export default function OpenSource() {
  return (
    <section className="py-28 px-6">
      <div className="max-w-6xl mx-auto">
        <div className="relative rounded-3xl border border-white/[0.07] bg-gradient-to-br from-surface to-s2 overflow-hidden">
          {/* Background glow */}
          <div className="absolute -top-40 -right-40 w-[500px] h-[500px] bg-violet-600/[0.08] rounded-full blur-[120px] pointer-events-none" />
          <div className="absolute -bottom-20 -left-20 w-[300px] h-[300px] bg-indigo-600/[0.06] rounded-full blur-[100px] pointer-events-none" />

          <div className="relative grid md:grid-cols-2 gap-10 p-10 md:p-16 items-center">
            {/* Text */}
            <motion.div
              initial={{ opacity: 0, x: -30 }}
              whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6, ease: 'easeOut' }}
            >
              <h2 className="text-3xl md:text-4xl font-bold tracking-tight mb-5 leading-tight">
                Free &amp; open source,{' '}
                <span
                  style={{
                    background: 'linear-gradient(135deg, #fff 20%, #A78BFA 100%)',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                    backgroundClip: 'text',
                  }}
                >
                  forever.
                </span>
              </h2>
              <p className="text-[#B0A8C8] leading-relaxed mb-8 text-[1.02rem]">
                Resonance is licensed under the MIT License. No ads, no tracking, no
                subscriptions — ever. The full source code is on GitHub and contributions are
                always welcome.
              </p>
              <a
                href={GITHUB_APP}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 bg-primary hover:bg-primary-light text-[#2E1065] font-semibold px-6 py-3 rounded-full text-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_14px_36px_rgba(167,139,250,0.4)]"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z" />
                </svg>
                View Source Code
              </a>
            </motion.div>

            {/* Code block */}
            <motion.div
              initial={{ opacity: 0, x: 30 }}
              whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6, ease: 'easeOut' }}
              className="rounded-2xl border border-white/[0.07] bg-[#06060B] overflow-hidden shadow-[0_20px_60px_rgba(0,0,0,0.5)]"
            >
              {/* Terminal chrome */}
              <div className="flex items-center gap-2 px-4 py-3 border-b border-white/[0.05]">
                <div className="w-3 h-3 rounded-full bg-[#FF5F57]" />
                <div className="w-3 h-3 rounded-full bg-[#FEBC2E]" />
                <div className="w-3 h-3 rounded-full bg-[#28C840]" />
                <span className="ml-2 text-xs text-[#4A4460] font-mono">bash</span>
              </div>
              {/* Code */}
              <pre className="p-6 text-sm font-mono leading-7 overflow-x-auto select-all">
                <code>
                  <span className="text-[#4A4460]"># Clone and build{'\n'}</span>
                  <span className="text-emerald-400">git</span>
                  <span className="text-[#C4B5FD]"> clone https://github.com/{'\n'}</span>
                  <span className="text-[#C4B5FD]">  yuw1xx/resonance-app.git{'\n'}</span>
                  <span className="text-emerald-400">cd</span>
                  <span className="text-[#C4B5FD]"> resonance-app{'\n'}</span>
                  <span className="text-[#C4B5FD]">./gradlew assembleDebug</span>
                </code>
              </pre>
            </motion.div>
          </div>
        </div>
      </div>
    </section>
  )
}
