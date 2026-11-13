import { api } from "./client";

/** Mirrors the backend's GroupDtos. */
export type ItemSummary = {
  itemId: number;
  title: string;
  venue: string;
  city: string;
  detail: string;
  startsAt: string;
  link: string;
  bookable: boolean;
};

export type GroupStatus = "OPEN" | "BOOKED" | "CANCELLED";

export type GroupCreated = { groupId: string; code: string; shareLink: string; whatsappShareUrl: string };
export type GroupPreview = { code: string; status: GroupStatus; memberCount: number; maxMembers: number; item: ItemSummary };
export type Member = {
  userId: string;
  name: string;
  avatarUrl: string | null;
  role: "LEADER" | "MEMBER";
  status: "JOINED" | "PENDING" | "PAID";
  you: boolean;
};
export type GroupView = {
  id: string;
  code: string;
  status: GroupStatus;
  maxMembers: number;
  youAreLeader: boolean;
  item: ItemSummary;
  members: Member[];
  shareLink: string;
  whatsappShareUrl: string;
};
export type GroupCard = { id: string; code: string; status: GroupStatus; memberCount: number; maxMembers: number; youAreLeader: boolean; item: ItemSummary };

export const groupApi = {
  /** solo = "Book for myself": a booking for one person, with no invites. */
  create: (showId: number, solo = false) => api<GroupCreated>("/groups", { method: "POST", body: { showId, solo }, auth: true }),
  preview: (code: string) => api<GroupPreview>(`/groups/preview/${encodeURIComponent(code)}`),
  join: (code: string) => api<{ groupId: string }>(`/groups/${encodeURIComponent(code)}/join`, { method: "POST", auth: true }),
  view: (id: string) => api<GroupView>(`/groups/${id}`, { auth: true }),
  mine: () => api<GroupCard[]>("/groups/my", { auth: true }),
  removeMember: (id: string, userId: string) => api(`/groups/${id}/members/${userId}`, { method: "DELETE", auth: true }),
  cancel: (id: string) => api(`/groups/${id}`, { method: "DELETE", auth: true }),
};

/** "Fri 14 Nov · 7:30 pm", always in IST. */
export const showWhen = (iso: string) =>
  new Date(iso).toLocaleString("en-IN", {
    weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit", timeZone: "Asia/Kolkata",
  });
