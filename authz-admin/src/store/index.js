import { defineStore } from 'pinia'
import { Master } from '@/api'

export const useAppStore = defineStore('app', {
  state: () => ({
    systemCd: localStorage.getItem('systemCd') || 'ERP',
    actor: 'leecy@hd.com',
    systems: []
  }),
  actions: {
    async loadSystems () {
      this.systems = await Master.systems()
      if (!this.systems.find(s => s.systemCd === this.systemCd) && this.systems.length) {
        this.setSystem(this.systems[0].systemCd)
      }
    },
    setSystem (cd) {
      this.systemCd = cd
      localStorage.setItem('systemCd', cd)
    }
  }
})
