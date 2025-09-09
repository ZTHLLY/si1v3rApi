// This file defines the types and interfaces used throughout the project to ensure type safety in TypeScript.

export interface LoginUserVO {
  userAvatar: string;
  userName: string;
  userRole: string;
}

export interface InitialState {
  currentUser?: LoginUserVO;
}